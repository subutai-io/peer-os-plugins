package io.subutai.plugin.appscale.impl.handler;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentModificationException;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.NodeSchema;
import io.subutai.common.environment.Topology;
import io.subutai.common.metric.QuotaAlertValue;
import io.subutai.common.peer.AlertHandlerException;
import io.subutai.common.peer.ContainerSize;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.ExceededQuotaAlertHandler;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.common.resource.ByteValueResource;
import io.subutai.common.resource.ContainerResourceType;
import io.subutai.common.resource.PeerGroupResources;
import io.subutai.common.resource.PeerResources;
import io.subutai.common.task.CloneRequest;
import io.subutai.core.strategy.api.StrategyException;
import io.subutai.plugin.appscale.api.AppScaleConfig;
import io.subutai.plugin.appscale.impl.AppScaleImpl;
import io.subutai.plugin.appscale.impl.AppscalePlacementStrategy;


/**
 * Node resource threshold excess alert listener
 */
public class AlertHandler extends ExceededQuotaAlertHandler
{
    private static final Logger LOG = LoggerFactory.getLogger( AlertHandler.class );
    private static final String HANDLER_ID = "DEFAULT_APPSCALE_QUOTA_EXCEEDED_ALERT_HANDLER";
    private AppScaleImpl appScale;
    private static Set<String> locks = new CopyOnWriteArraySet<>();


    public AlertHandler( final AppScaleImpl appScale )
    {
        this.appScale = appScale;
    }


    private void throwAlertException( String context, Exception e ) throws AlertHandlerException
    {
        LOG.error( context, e );
        throw new AlertHandlerException( context, e );
    }


    @Override
    public String getId()
    {
        return HANDLER_ID;
    }


    @Override
    public String getDescription()
    {
        return "Node resource threshold excess default alert handler for appScale.";
    }


    @Override
    public void process( final Environment environment, final QuotaAlertValue alertValue ) throws AlertHandlerException
    {
        LOG.debug( String.format( "%s", alertValue ) );
        //find appScale cluster by environment id
        final List<AppScaleConfig> clusters = appScale.getClusters();

        String environmentId = environment.getId();

        AppScaleConfig targetCluster = null;
        for ( AppScaleConfig cluster : clusters )
        {
            if ( cluster.getEnvironmentId().equals( environmentId ) )
            {
                targetCluster = cluster;
                break;
            }
        }

        if ( targetCluster == null )
        {
            throwAlertException( String.format( "Cluster not found by environment id %s", environmentId ), null );
            return;
        }

        EnvironmentContainerHost sourceHost = getSourceHost();

        if ( sourceHost == null )
        {
            throwAlertException( String.format( "Alert source host %s not found in environment",
                    alertValue.getValue().getHostId().getId() ), null );
            return;
        }

        //check if source host belongs to found appScale cluster
        if ( !targetCluster.getNodes().contains( sourceHost.getId() ) )
        {
            LOG.info( String.format( "Alert source host %s does not belong to AppScale cluster",
                    alertValue.getValue().getHostId() ) );
            return;
        }


        if ( alertValue.getValue().getContainerResourceType() == ContainerResourceType.HOME )
        {
            final ByteValueResource current = alertValue.getValue().getCurrentValue( ByteValueResource.class );
            final ByteValueResource quota = alertValue.getValue().getQuotaValue( ByteValueResource.class );
            if ( current == null || quota == null )
            {
                // invalid value
                return;
            }

            boolean isStressed = quota.getValue().compareTo( current.getValue() ) < 1
                    || quota.getValue().multiply( new BigDecimal( "0.8" ) ).compareTo( current.getValue() ) < 1;


            if ( isStressed )
            {
                createNewInstance( environment );
            }
        }
    }


    private void createNewInstance( Environment environment )
    {
        if ( isLocked( environment.getId() ) )
        {
            LOG.debug( "Environment is locked. Skipping." );
        }

        try
        {
            lock( environment.getId() );

            final PeerGroupResources peerGroupResources = appScale.getPeerManager().getPeerGroupResources();
            final Map<ContainerSize, ContainerQuota> quotas = appScale.getQuotaManager().getDefaultQuotas();

            final List<PeerResources> resources = new ArrayList<>();
            final Set<String> preferredPeers = getPreferredPeers( environment );

            for ( final PeerResources peerResources : peerGroupResources.getResources() )
            {
                if ( preferredPeers.contains( peerResources.getPeerId() ) )
                {
                    resources.add( peerResources );
                }
            }

            PeerGroupResources neighbors = new PeerGroupResources( resources, peerGroupResources.getDistances() );

            Topology topology = null;
            try
            {
                topology = new AppscalePlacementStrategy().distribute( environment.getName(), neighbors, quotas );
            }
            catch ( StrategyException e )
            {
                // ignore
            }

            if ( topology == null )
            {
                topology =
                        new AppscalePlacementStrategy().distribute( environment.getName(), peerGroupResources, quotas );
            }

            final Set<EnvironmentContainerHost> result =
                    appScale.getEnvironmentManager().growEnvironment( environment.getId(), topology, false );

            // grow succeeded
            //TODO: need config new appscale instance
        }
        catch ( PeerException | EnvironmentModificationException | EnvironmentNotFoundException | StrategyException e )
        {
            LOG.error( e.getMessage(), e );
        }
        finally
        {
            unlock( environment.getId() );
        }
    }


    private Set<String> getPreferredPeers( final Environment environment )
    {
        final Set<String> result = new HashSet<>();
        for ( EnvironmentContainerHost c : environment.getContainerHosts() )
        {
            result.add( c.getPeerId() );
        }
        return result;
    }


    private void unlock( final String environmentId )
    {
        locks.remove( environmentId );
    }


    private void lock( final String environmentId )
    {
        locks.add( environmentId );
    }


    private boolean isLocked( final String environmentId )
    {
        return locks.contains( environmentId );
    }
}

