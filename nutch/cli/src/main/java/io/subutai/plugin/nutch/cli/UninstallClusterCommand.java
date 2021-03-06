package io.subutai.plugin.nutch.cli;


import java.util.UUID;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

import io.subutai.core.tracker.api.Tracker;
import io.subutai.plugin.nutch.api.Nutch;
import io.subutai.plugin.nutch.api.NutchConfig;


@Command( scope = "nutch", name = "uninstall-cluster", description = "Command to uninstall Nutch cluster" )
public class UninstallClusterCommand extends OsgiCommandSupport
{

    @Argument( index = 0, name = "clusterName", description = "The name of the cluster.", required = true,
            multiValued = false )
    String clusterName = null;
    private Nutch nutchManager;
    private Tracker tracker;


    public void setTracker( Tracker tracker )
    {
        this.tracker = tracker;
    }


    public void setNutchManager( Nutch nutchManager )
    {
        this.nutchManager = nutchManager;
    }


    protected Object doExecute()
    {
        UUID uuid = nutchManager.uninstallCluster( clusterName );

        tracker.printOperationLog( NutchConfig.PRODUCT_KEY, uuid, 10 * 60 * 1000 );

        return null;
    }
}
