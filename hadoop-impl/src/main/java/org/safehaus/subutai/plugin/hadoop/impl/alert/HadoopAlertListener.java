package org.safehaus.subutai.plugin.hadoop.impl.alert;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.safehaus.subutai.common.command.CommandResult;
import org.safehaus.subutai.common.command.CommandUtil;
import org.safehaus.subutai.common.command.RequestBuilder;
import org.safehaus.subutai.common.environment.Environment;
import org.safehaus.subutai.common.environment.EnvironmentNotFoundException;
import org.safehaus.subutai.common.metric.ProcessResourceUsage;
import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.core.metric.api.AlertListener;
import org.safehaus.subutai.core.metric.api.ContainerHostMetric;
import org.safehaus.subutai.core.metric.api.MonitoringSettings;
import org.safehaus.subutai.plugin.common.api.NodeType;
import org.safehaus.subutai.plugin.hadoop.api.HadoopClusterConfig;
import org.safehaus.subutai.plugin.hadoop.impl.Commands;
import org.safehaus.subutai.plugin.hadoop.impl.HadoopImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Node resource threshold excess alert listener
 */
public class HadoopAlertListener implements AlertListener
{
    private static final Logger LOG = LoggerFactory.getLogger( HadoopAlertListener.class.getName() );
    public static final String HADOOP_ALERT_LISTENER = "HADOOP_ALERT_LISTENER";
    private HadoopImpl hadoop;
    private CommandUtil commandUtil = new CommandUtil();
    private static double MAX_RAM_QUOTA_MB;
    private static int RAM_QUOTA_INCREMENT_PERCENTAGE = 25;
    private static int MAX_CPU_QUOTA_PERCENT = 80;
    private static int CPU_QUOTA_INCREMENT_PERCENT = 10;
    private static final String PID_STRING = "pid";


    public HadoopAlertListener( final HadoopImpl hadoop )
    {
        this.hadoop = hadoop;
    }


    private void throwAlertException( String context, Exception e ) throws AlertException
    {
        LOG.error( context, e );
        throw new AlertException( context, e );
    }


    @Override
    public void onAlert( final ContainerHostMetric metric ) throws Exception
    {
        //find hadoop cluster by environment id
        List<HadoopClusterConfig> clusters = hadoop.getClusters();

        HadoopClusterConfig targetCluster = null;
        for ( HadoopClusterConfig cluster : clusters )
        {
            if ( cluster.getEnvironmentId().equals( metric.getEnvironmentId() ) )
            {
                targetCluster = cluster;
                break;
            }
        }

        if ( targetCluster == null )
        {
            throwAlertException( String.format( "Cluster not found by environment id %s", metric.getEnvironmentId() ),
                    null );
            return;
        }

        //get cluster environment
        Environment environment;
        ContainerHost sourceHost;
        try
        {
            environment = hadoop.getEnvironmentManager().findEnvironment( metric.getEnvironmentId() );

            //get environment containers and find alert's source host
            Set<ContainerHost> containers = environment.getContainerHosts();

            sourceHost = null;
            for ( ContainerHost containerHost : containers )
            {
                if ( containerHost.getHostname().equalsIgnoreCase( metric.getHost() ) )
                {
                    sourceHost = containerHost;
                    break;
                }
            }

        } catch ( EnvironmentNotFoundException e ){
            LOG.error( "Could not find environment.", e );
            e.printStackTrace();
            return;
        }


        if ( sourceHost == null )
        {
            throwAlertException( String.format( "Alert source host %s not found in environment", metric.getHost() ),
                    null );
            return;
        }

        //check if source host belongs to found hadoop cluster
        if ( !targetCluster.getAllNodes().contains( sourceHost.getId() ) )
        {
            LOG.info( String.format( "Alert source host %s does not belong to Hadoop cluster", metric.getHost() ) );
            return;
        }

        // Set 80 percent of the available ram capacity of the resource host
        // to maximum ram quota limit assignable to the container
        MAX_RAM_QUOTA_MB = sourceHost.getAvailableRamQuota() * 0.8;

        List<NodeType> nodeRoles = HadoopClusterConfig.getNodeRoles( targetCluster, sourceHost );

        double totalRamUsage = 0;
        double totalCpuUsage = 0;
        double redLine = 0.7;

        // confirm that Hadoop is causing the stress, otherwise no-op
        MonitoringSettings thresholds = hadoop.getAlertSettings();
        double ramLimit = metric.getTotalRam() * ( (double) thresholds.getRamAlertThreshold() / 100 );
        HashMap<NodeType, Double> ramConsumption = new HashMap<>();
        HashMap<NodeType, Double> cpuConsumption = new HashMap<>();

        for ( NodeType nodeType : nodeRoles )
        {
            int pid;
            switch ( nodeType )
            {
                case NAMENODE:
                    CommandResult result = commandUtil
                            .execute( new RequestBuilder( Commands.getStatusNameNodeCommand() ), sourceHost );
                    String output = parseService( result.getStdOut(), nodeType.name().toLowerCase() );
                    if ( ! output.toLowerCase().contains( PID_STRING ) ){
                        break;
                    }
                    pid = parsePid( output );
                    ProcessResourceUsage processResourceUsage = sourceHost.getProcessResourceUsage( pid );
                    ramConsumption.put( NodeType.NAMENODE, processResourceUsage.getUsedRam() );
                    cpuConsumption.put( NodeType.NAMENODE, processResourceUsage.getUsedCpu() );
                    break;
                case SECONDARY_NAMENODE:
                    result = commandUtil.execute( new RequestBuilder( Commands.getStatusNameNodeCommand() ),
                            sourceHost );
                    output = parseService( result.getStdOut(), "secondarynamenode" );
                    if ( ! output.toLowerCase().contains( PID_STRING ) ){
                        break;
                    }
                    pid = parsePid( output );
                    processResourceUsage = sourceHost.getProcessResourceUsage( pid );
                    ramConsumption.put( NodeType.SECONDARY_NAMENODE, processResourceUsage.getUsedRam() );
                    cpuConsumption.put( NodeType.SECONDARY_NAMENODE, processResourceUsage.getUsedCpu() );
                    break;
                case JOBTRACKER:
                    result = commandUtil
                            .execute( new RequestBuilder( Commands.getStatusJobTrackerCommand() ), sourceHost );
                    output = parseService( result.getStdOut(),  nodeType.name().toLowerCase() );
                    if ( ! output.toLowerCase().contains( PID_STRING ) ){
                        break;
                    }
                    pid = parsePid( output );
                    processResourceUsage = sourceHost.getProcessResourceUsage( pid );
                    ramConsumption.put( NodeType.JOBTRACKER, processResourceUsage.getUsedRam() );
                    cpuConsumption.put( NodeType.JOBTRACKER, processResourceUsage.getUsedCpu() );
                    break;
                case DATANODE:
                    result = commandUtil
                            .execute( new RequestBuilder( Commands.getStatusDataNodeCommand() ), sourceHost );
                    output = parseService( result.getStdOut(),  nodeType.name().toLowerCase() );
                    if ( ! output.toLowerCase().contains( PID_STRING ) ){
                        break;
                    }
                    pid = parsePid( output );
                    processResourceUsage = sourceHost.getProcessResourceUsage( pid );
                    ramConsumption.put( NodeType.DATANODE, processResourceUsage.getUsedRam() );
                    cpuConsumption.put( NodeType.DATANODE, processResourceUsage.getUsedCpu() );
                case TASKTRACKER:
                    result = commandUtil
                            .execute( new RequestBuilder( Commands.getStatusTaskTrackerCommand() ), sourceHost );
                    output = parseService( result.getStdOut(),  nodeType.name().toLowerCase() );
                    if ( ! output.toLowerCase().contains( PID_STRING ) ){
                        break;
                    }
                    pid = parsePid( output );
                    processResourceUsage = sourceHost.getProcessResourceUsage( pid );
                    ramConsumption.put( NodeType.TASKTRACKER, processResourceUsage.getUsedRam() );
                    cpuConsumption.put( NodeType.TASKTRACKER, processResourceUsage.getUsedCpu() );
                    break;
            }
        }

        for ( NodeType nodeType : nodeRoles )
        {
            if ( ramConsumption.get( nodeType ) != null ){
                totalRamUsage += ramConsumption.get( nodeType );
            }
            if ( cpuConsumption.get( nodeType ) != null ){
                totalCpuUsage += cpuConsumption.get( nodeType );
            }
        }


        boolean isCPUStressedByHadoop = false;
        boolean isRAMStressedByHadoop = false;

        if ( totalRamUsage >= ramLimit * redLine )
        {
            isRAMStressedByHadoop = true;
        }

        if ( totalCpuUsage >= thresholds.getCpuAlertThreshold() * redLine )
        {
            isCPUStressedByHadoop = true;
        }

        if ( !( isRAMStressedByHadoop || isCPUStressedByHadoop ) )
        {
            LOG.info( "Hadoop cluster ok" );
            return;
        }

        /**
         * after this point, we found out source node is under stress, we need to either
         * scale vertically ( increase available sources) or scale horizontally ( add new nodes to cluster)
         *
         * Since in hadoop master nodes cannot be scaled horizontally ( Hadoop can just have one NameNode, one
         * JobTracker, one SecondaryNameNode ), we should scale master nodes vertically. However we can scale
         * out slave nodes (DataNode and TaskTracker) horizontally.
         *
         * Vertical scaling have more priority to Horizontal scaling.
         */

        //auto-scaling is enabled -> scale cluster
        if ( targetCluster.isAutoScaling() )
        {
            // check if a quota limit increase does it
            boolean quotaIncreased = false;

            if ( isRAMStressedByHadoop )
            {
                //read current RAM quota
                int ramQuota = sourceHost.getRamQuota();


                if ( ramQuota < MAX_RAM_QUOTA_MB )
                {
                    // if available quota on resource host is greater than 10 % of calculated increase amount,
                    // increase quota, otherwise scale horizontally
                    int newRamQuota = ramQuota * ( 100 + RAM_QUOTA_INCREMENT_PERCENTAGE ) / 100;
                    if ( MAX_RAM_QUOTA_MB > newRamQuota ) {

                        LOG.info( "Increasing ram quota of {} from {} MB to {} MB.",
                                sourceHost.getHostname(), sourceHost.getRamQuota(), newRamQuota );
                        //we can increase RAM quota
                        sourceHost.setRamQuota( newRamQuota );

                        quotaIncreased = true;
                    }
                }
            }

            if ( isCPUStressedByHadoop )
            {

                //read current CPU quota
                int cpuQuota = sourceHost.getCpuQuota();

                if ( cpuQuota < MAX_CPU_QUOTA_PERCENT )
                {
                    int newCpuQuota = Math.min( MAX_CPU_QUOTA_PERCENT, cpuQuota + CPU_QUOTA_INCREMENT_PERCENT );
                    LOG.info( "Increasing cpu quota of {} from {}% to {}%.",
                            sourceHost.getHostname(), cpuQuota, newCpuQuota );

                    //we can increase CPU quota
                    sourceHost.setCpuQuota( newCpuQuota );

                    quotaIncreased = true;
                }
            }

            //quota increase is made, return
            if ( quotaIncreased )
            {
                return;
            }

            /**
             * If one of slave nodes uses consume most resources on machines,
             * we can scale horizontally, otherwise do nothing.
             */
            if ( isSourceNodeUnderStressBySlaveNodes( ramConsumption, cpuConsumption, targetCluster ) )
            {
                // add new nodes to hadoop cluster (horizontal scaling)
                hadoop.addNode( targetCluster.getClusterName() );
            }
        }
        else
        {
            notifyUser();
        }
    }


    private boolean isSourceNodeUnderStressBySlaveNodes( HashMap<NodeType, Double> ramConsumption,
                                                         HashMap<NodeType, Double> cpuConsumption,
                                                         HadoopClusterConfig targetCluster )
    {
        Map.Entry<NodeType, Double> maxEntryInRamConsumption = null;
        for ( Map.Entry<NodeType, Double> entry : ramConsumption.entrySet() )
        {
            if ( maxEntryInRamConsumption == null
                    || entry.getValue().compareTo( maxEntryInRamConsumption.getValue() ) > 0 )
            {
                maxEntryInRamConsumption = entry;
            }
        }

        Map.Entry<NodeType, Double> maxEntryInCPUConsumption = null;
        for ( Map.Entry<NodeType, Double> entry : cpuConsumption.entrySet() )
        {
            if ( maxEntryInCPUConsumption == null
                    || entry.getValue().compareTo( maxEntryInCPUConsumption.getValue() ) > 0 )
            {
                maxEntryInCPUConsumption = entry;
            }
        }

        assert maxEntryInCPUConsumption != null;
        assert maxEntryInRamConsumption != null;
        return maxEntryInCPUConsumption.getKey().equals( NodeType.DATANODE ) ||
                maxEntryInCPUConsumption.getKey().equals( NodeType.TASKTRACKER ) ||
                maxEntryInRamConsumption.getKey().equals( NodeType.DATANODE ) ||
                maxEntryInRamConsumption.getKey().equals( NodeType.TASKTRACKER );
    }


    protected String parseService ( String output, String target ){
        String inputArray[] = output.split( "\n" );
        for ( String part : inputArray ){
            if ( part.toLowerCase().contains( target ) ){
                return part;
            }
        }
        return null;
    }


    protected int parsePid( String output ) throws AlertException
    {
        Pattern p = Pattern.compile( "pid\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE );

        Matcher m = p.matcher( output );

        if ( m.find() )
        {
            return Integer.parseInt( m.group( 1 ) );
        }
        else
        {
            throwAlertException( String.format( "Could not parse PID from %s", output ), null );
        }
        return 0;
    }


    protected void notifyUser()
    {
        //TODO implement me when user identity management is complete and we can figure out user email
    }


    @Override
    public String getSubscriberId()
    {
        return HADOOP_ALERT_LISTENER;
    }
}

