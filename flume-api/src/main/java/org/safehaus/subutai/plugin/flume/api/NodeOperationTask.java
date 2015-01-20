package org.safehaus.subutai.plugin.flume.api;


import java.util.UUID;

import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.common.api.CompleteEvent;
import org.safehaus.subutai.plugin.common.api.NodeOperationType;
import org.safehaus.subutai.plugin.common.impl.AbstractNodeOperationTask;


public class NodeOperationTask extends AbstractNodeOperationTask implements Runnable
{

    private final String clusterName;
    private final ContainerHost containerHost;
    private final Flume flume;
    private final NodeOperationType operationType;


    public NodeOperationTask( Flume flume, Tracker tracker, String clusterName, ContainerHost containerHost,
                              NodeOperationType operationType, CompleteEvent completeEvent, UUID trackID )
    {
        super( tracker, flume.getCluster( clusterName ), completeEvent, trackID, containerHost );
        this.flume = flume;
        this.clusterName = clusterName;
        this.containerHost = containerHost;
        this.operationType = operationType;
    }


    @Override
    public UUID runTask()
    {
        UUID trackID = null;
        switch ( operationType )
        {
            case START:
                trackID = flume.startNode( clusterName, containerHost.getHostname() );
                break;
            case STOP:
                trackID = flume.stopNode( clusterName, containerHost.getHostname() );
                break;
            case STATUS:
                trackID = flume.checkServiceStatus( clusterName, containerHost.getHostname() );
                break;
        }
        return trackID;
    }


    @Override
    public String getProductStoppedIdentifier()
    {
        return "Flume is not running";
    }


    @Override
    public String getProductRunningIdentifier()
    {
        return "Flume is running";
    }
}
