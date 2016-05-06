/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.subutai.plugin.appscale.impl;


import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.time.DateUtils;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.host.HostId;
import io.subutai.common.peer.AlertHandlerPriority;
import io.subutai.common.peer.ContainerSize;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.HostNotFoundException;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.settings.Common;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.core.environment.api.exception.EnvironmentManagerException;
import io.subutai.core.identity.api.IdentityManager;
import io.subutai.core.identity.api.model.UserToken;
import io.subutai.core.peer.api.PeerManager;
import io.subutai.core.plugincommon.api.ClusterConfigurationException;
import io.subutai.core.plugincommon.api.ClusterConfigurationInterface;
import io.subutai.core.plugincommon.api.ConfigBase;
import io.subutai.plugin.appscale.api.AppScaleConfig;
import io.subutai.plugin.appscale.impl.handler.AppscaleAlertHandler;


public class ClusterConfiguration implements ClusterConfigurationInterface
{

    private final LocalPeer localPeer;
    private final TrackerOperation po;
    private final AppScaleImpl appscaleManager;
    private EnvironmentContainerHost containerHost;


    private static final Logger LOG = LoggerFactory.getLogger( ClusterConfiguration.class.getName() );


    public ClusterConfiguration( final TrackerOperation operation, final AppScaleImpl appScaleImpl, final PeerManager peerManager )
    {
        this.po = operation;
        this.appscaleManager = appScaleImpl;
        this.localPeer = peerManager.getLocalPeer();
    }


    @Override
    public void configureCluster( ConfigBase configBase, Environment environment ) throws ClusterConfigurationException
    {
        LOG.info ("in configureCluster");
        AppScaleConfig config = ( AppScaleConfig ) configBase;
        this.installCluster( configBase, environment );
    }


    private void installCluster( ConfigBase configBase, Environment environment )
    {
        AppScaleConfig config = ( AppScaleConfig ) configBase;
        String domain = config.getDomain();

        if ( domain == null )
        {
            po.addLogFailed( "Missing domain for appscale" );
        }

        Set<EnvironmentContainerHost> cn = environment.getContainerHosts();
        int numberOfContainers = cn.size();


        try
        {
            // this will be our controller container.
            containerHost = environment.getContainerHostByHostname( config.getMasterNode() );
            if ( containerHost.getContainerSize() != ContainerSize.HUGE )
            {
                LOG.error( "Please make sure your containers' sizes are HUGE, disk quota is too small for your containers" );
                po.addLogFailed( "Please make sure your containers' sizes are HUGE, disk quota is too small for your containers" );
            }
        }
        catch ( ContainerHostNotFoundException ex )
        {
            LOG.error( ex.getMessage() );
            po.addLogFailed( "Container Host Not Found" );
        }

        LOG.info ("Preparing AppScalefile");
        // AppScalefile configuration
        this.appscaleInitCluster( containerHost, config );

        LOG.info ("Preparing domain and starting cluster");
        // appscale up and domain management
        this.runAfterInitCommands( containerHost, config );
        LOG.info ("Configuring proxy on RH");
        // configure proxy on rh
        this.configureRH ( containerHost, config );

/*        try
        {
            appscaleManager.getEnvironmentManager()
                           .startMonitoring( AppscaleAlertHandler.HANDLER_ID, AlertHandlerPriority.NORMAL,
                                   environment.getId() );
            po.addLog( "Alert handler added successfully." );
        }
        catch ( EnvironmentManagerException e )
        {
            LOG.error( e.getMessage(), e );
            po.addLogFailed( "Could not add alert handler to monitor this environment." );
        }*/
        LOG.info( "Appscale saved to database" );
    }


    private void configureRH( EnvironmentContainerHost containerHost, AppScaleConfig config )
    {
        try
        {
            ResourceHost resourceHostByContainerId = localPeer.getResourceHostByContainerId( containerHost.getId() );

            CommandResult resultStr = resourceHostByContainerId
                    .execute ( new RequestBuilder ( "grep vlan /mnt/lib/lxc/" + config.getClusterName() + "/config" ) );

            String stdOut = resultStr.getStdOut ();

            String vlanString = stdOut.substring ( 11, 14 );

            resourceHostByContainerId.execute ( new RequestBuilder ( "subutai proxy del " + vlanString + " -d" ) );

            resourceHostByContainerId.execute ( new RequestBuilder (
                    "subutai proxy add " + vlanString + " -d \"*." + config.getDomain () + "\" -f /mnt/lib/lxc/"
                            + config.getClusterName() + "/rootfs/etc/nginx/ssl.pem" ) );

            resourceHostByContainerId
                    .execute ( new RequestBuilder ( "subutai proxy add " + vlanString + " -h " + config.getMasterNode() ) );
        }
        catch ( Exception e )
        {
            LOG.error( "Error to set proxy settings: ", e );
        }
    }


    private void commandExecute( EnvironmentContainerHost containerHost, String command )
    {
        try
        {
            containerHost.execute( new RequestBuilder( command ).withTimeout( 10000 ).withCwd( "/root" ) );
        }
        catch ( CommandException e )
        {
            LOG.error( "Error while executing \"" + command + "\".\n" + e );
        }
    }


    private void appscaleInitCluster( EnvironmentContainerHost containerHost, AppScaleConfig config )
    {
        String command = "bash /var/lib/subutai-appscale/create-appscalefile.sh -master " + config.getMasterNode() + " -appengine";
        for (int i = 0; i < config.getAppengineNodes().size(); ++i)
        {
            command += " " + config.getAppengineNodes().get( i );
        }
        command += " -database";
        for (int i = 0; i < config.getCassandraNodes().size(); ++i)
        {
            command += " " + config.getCassandraNodes().get( i );
        }
        command += " -zookeeper ";
        for (int i = 0; i < config.getZookeeperNodes().size(); ++i)
        {
            command += " " + config.getZookeeperNodes().get( i );
        }
        LOG.info ("Executing: " + command);
        this.commandExecute( containerHost, command );
    }




    private void runAfterInitCommands( EnvironmentContainerHost containerHost, AppScaleConfig config )
    {
        String command = "bash /var/lib/subutai-appscale/setup.sh " + config.getDomain() + " " + config.getLogin() + " " + config.getPassword();
        LOG.info ("Executing: " + command);
        this.commandExecute( containerHost, command );
    }
}

