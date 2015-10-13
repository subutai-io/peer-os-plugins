/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.subutai.plugin.hbase.ui.wizard;


import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Window;

import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.core.tracker.api.Tracker;
import io.subutai.plugin.common.ui.ConfigView;
import io.subutai.plugin.hbase.api.HBase;
import io.subutai.plugin.hbase.api.HBaseConfig;
import io.subutai.server.ui.component.ProgressWindow;


public class VerificationStep extends Panel
{

    private static final Logger LOGGER = LoggerFactory.getLogger( VerificationStep.class );


    public VerificationStep( final Tracker tracker, final HBase hbase, final ExecutorService executor,
                             final Wizard wizard )
    {

        setSizeFull();

        GridLayout grid = new GridLayout( 1, 5 );
        grid.setSpacing( true );
        grid.setMargin( true );
        grid.setSizeFull();

        Label confirmationLbl = new Label( "<strong>Please verify the installation settings "
                + "(you may change them by clicking on Back button)</strong><br/>" );
        confirmationLbl.setContentMode( ContentMode.HTML );

        final HBaseConfig config = wizard.getConfig();
        Environment environment;
        try
        {
            environment = wizard.getEnvironmentManager().loadEnvironment( config.getEnvironmentId() );
        }
        catch ( EnvironmentNotFoundException e )
        {
            LOGGER.error( "Environment not found.", e );
            return;
        }

        ConfigView cfgView = new ConfigView( "Installation configuration" );
        cfgView.addStringCfg( "HBase Cluster Name", wizard.getConfig().getClusterName() );

        try
        {
            cfgView.addStringCfg( "Hadoop cluster Name", wizard.getConfig().getHadoopClusterName() );
            cfgView.addStringCfg( "Master Node",
                    environment.getContainerHostById( wizard.getConfig().getHbaseMaster() ).getHostname() );
            for ( String host : wizard.getConfig().getRegionServers() )
            {
                cfgView.addStringCfg( "Region Servers", environment.getContainerHostById( host ).getHostname() );
            }

            for ( String host : wizard.getConfig().getQuorumPeers() )
            {
                cfgView.addStringCfg( "Quorum Peers", environment.getContainerHostById( host ).getHostname() );
            }

            for ( String host : wizard.getConfig().getBackupMasters() )
            {
                cfgView.addStringCfg( "Backup Masters", environment.getContainerHostById( host ).getHostname() );
            }
            cfgView.addStringCfg( "Environment ID", config.getEnvironmentId() );
        }
        catch ( ContainerHostNotFoundException e )
        {
            LOGGER.error( "Container hosts not found", e );
            Notification.show( "Error accessing container host", "Couldn't get some container hosts",
                    Notification.Type.ERROR_MESSAGE );
            return;
        }


        Button install = new Button( "Install" );
        install.addStyleName( "default" );
        install.setId( "HbaseVerificationInstall" );
        install.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent )
            {
                UUID trackId = hbase.installCluster( config );
                ProgressWindow window = new ProgressWindow( executor, tracker, trackId, HBaseConfig.PRODUCT_KEY );
                window.getWindow().addCloseListener( new Window.CloseListener()
                {
                    @Override
                    public void windowClose( Window.CloseEvent closeEvent )
                    {
                        wizard.init();
                    }
                } );
                getUI().addWindow( window.getWindow() );
            }
        } );

        Button back = new Button( "Back" );
        back.addStyleName( "default" );
        back.setId( "HbaseVerificationBack" );
        back.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent )
            {
                wizard.back();
            }
        } );

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.addComponent( back );
        buttons.addComponent( install );

        grid.addComponent( confirmationLbl, 0, 0 );

        grid.addComponent( cfgView.getCfgTable(), 0, 1, 0, 3 );

        grid.addComponent( buttons, 0, 4 );

        setContent( grid );
    }
}
