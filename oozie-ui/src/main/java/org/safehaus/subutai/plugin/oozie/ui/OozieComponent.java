package org.safehaus.subutai.plugin.oozie.ui;


import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.VerticalLayout;
import org.safehaus.subutai.core.environment.api.EnvironmentManager;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.hadoop.api.Hadoop;
import org.safehaus.subutai.plugin.oozie.api.Oozie;
import org.safehaus.subutai.plugin.oozie.ui.manager.Manager;
import org.safehaus.subutai.plugin.oozie.ui.wizard.Wizard;

import javax.naming.NamingException;
import java.util.concurrent.ExecutorService;


public class OozieComponent extends CustomComponent
{

    private final Wizard wizard;
    private final Manager manager;


    public OozieComponent( ExecutorService executorService, Oozie oozie, Hadoop hadoop, Tracker tracker, EnvironmentManager environmentManager) throws NamingException
    {


        setSizeFull();

        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.setSpacing( true );
        verticalLayout.setSizeFull();

        TabSheet sheet = new TabSheet();
        sheet.setSizeFull();
        manager = new Manager( executorService, oozie, hadoop, tracker, environmentManager);
        wizard = new Wizard( executorService, oozie, hadoop, tracker, environmentManager);
        sheet.addTab( wizard.getContent(), "Install" );
        sheet.getTab( 0 ).setId( "OozieInstallTab" );
        sheet.addTab( manager.getContent(), "Manage" );
        sheet.getTab( 1 ).setId( "OozieManageTab" );
        sheet.addSelectedTabChangeListener( new TabSheet.SelectedTabChangeListener()
        {
            @Override
            public void selectedTabChange( TabSheet.SelectedTabChangeEvent event )
            {
                TabSheet tabsheet = event.getTabSheet();
                String caption = tabsheet.getTab( event.getTabSheet().getSelectedTab() ).getCaption();
                if ( caption.equals( "Manage" ) )
                {
                    manager.refreshClustersInfo();
                }
            }
        } );
        verticalLayout.addComponent( sheet );
        setCompositionRoot( verticalLayout );
    }
}
