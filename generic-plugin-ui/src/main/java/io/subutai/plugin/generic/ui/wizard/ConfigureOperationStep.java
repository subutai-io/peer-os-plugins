package io.subutai.plugin.generic.ui.wizard;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import com.vaadin.data.Property;
import com.vaadin.server.Page;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

import io.subutai.plugin.generic.api.GenericPlugin;
import io.subutai.plugin.generic.api.model.Operation;
import io.subutai.plugin.generic.api.model.Profile;
import io.subutai.server.ui.component.ConfirmationDialog;


public class ConfigureOperationStep extends Panel
{
    private static final Logger LOG = LoggerFactory.getLogger( ConfigureOperationStep.class.getName() );
    protected static final String BUTTON_STYLE_NAME = "default";
    private TextField cwd = new TextField( "CWD" );
    private TextField timeOut = new TextField( "Timeout" );
    private CheckBox daemon = new CheckBox( "Daemon" );

    private GenericPlugin genericPlugin;
    private VerticalLayout content;
    private Label title;
    private ComboBox profileSelect;
    private HorizontalLayout fieldGrid;
    private Profile profile;

    private Table operationTable;
    private TextField newCommand;
    private TextField newName;
    private Window uploadWindow;
    private Wizard wizard;
    private boolean fromFile = false;
    private CheckBox scriptCheck;
    private Button addOperation;
    private Button uploadScript;
    private Button showAddOperationWindow;
    private TextArea commandArea;


    class MyUploader implements Upload.Receiver, Upload.SucceededListener, Upload.FailedListener
    {
        private File file;
        private String path;


        @Override
        public OutputStream receiveUpload( String filename, String MIMEType )
        {
            if ( filename != null && !filename.isEmpty() )
            {
                FileOutputStream fos;
                new File( "/tmp/uploads" ).mkdirs();
                this.file = new File( "/tmp/uploads/" + filename );
                this.path = new String( "/tmp/uploads/" + filename );
                try
                {
                    fos = new FileOutputStream( this.file );
                }
                catch ( final java.io.FileNotFoundException e )
                {
                    // Error while opening the file
                    // TODO: notify that file is not opened
                    return null;
                }

                return fos;
            }
            else
            {
                show( "Please select file" );
                return null;
            }
        }


        @Override
        public void uploadSucceeded( Upload.SucceededEvent event )
        {
            try
            {
                byte[] encoded = Files.readAllBytes( Paths.get( this.path ) );
                commandArea.setValue( new String( encoded ) );
                FileUtils.deleteDirectory( new File( "/tmp" ) );
                fromFile = true;
                uploadWindow.close();
                scriptCheck.setValue( true );
                newCommand.setEnabled( false );
                show( "File is uploaded" );
            }
            catch ( IOException e )
            {
                show( "Server is busy, please try again" );
                // TODO: file is not saved
            }
        }


        @Override
        public void uploadFailed( Upload.FailedEvent event )
        {
            show( "Upload failed, please try again" );
            // TODO: Log the failure on screen.
        }
    }


    public ConfigureOperationStep( final Wizard wizard, final GenericPlugin genericPlugin )
    {
        this.genericPlugin = genericPlugin;
        this.wizard = wizard;

        operationTable = createTableTemplate( "Operations" );

        content = new VerticalLayout();
        title = new Label( "<h2>Configure operations</h2>" );
        title.setContentMode( ContentMode.HTML );

        profileSelect = new ComboBox( "Choose Profile" );
        profileSelect.setNullSelectionAllowed( false );
        profileSelect.setTextInputAllowed( false );
        profileSelect.addValueChangeListener( new Property.ValueChangeListener()
        {
            @Override
            public void valueChange( Property.ValueChangeEvent event )
            {
                profile = ( Profile ) event.getProperty().getValue();
                refreshUI();
            }
        } );
        refreshProfileInfo();

        fieldGrid = new HorizontalLayout();
        fieldGrid.setSpacing( true );
        fieldGrid.setWidth( "100%" );

        newName = new TextField( "Operation name" );
        newName.setInputPrompt( "Enter new operation name" );
        newName.setRequired( true );
        newName.setWidth( "30%" );

        newCommand = new TextField( "Command" );
        newCommand.setRequired( true );
        newCommand.setInputPrompt( "Enter new command" );

        commandArea = new TextArea( "Command" );
        commandArea.setRequired( true );
        commandArea.setInputPrompt( "Enter new command" );
        commandArea.setRequired( true );
        commandArea.setWidth( "100%" );

        cwd.setValue( "/" );
        cwd.setData( "/" );
        timeOut.setValue( "30" );
        timeOut.setData( "30" );
        daemon.setValue( false );
        daemon.setData( false );

        scriptCheck = new CheckBox( "Script" );
        scriptCheck.setEnabled( false );
        scriptCheck.setValue( false );

        showAddOperationWindow = new Button( "Add Operation" );
        showAddOperationWindow.addStyleName( BUTTON_STYLE_NAME );

        showAddOperationWindow.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( final Button.ClickEvent clickEvent )
            {
                cleanTextBoxes();
                final Window subWindow = createWindowTemplate( "Adding operation" );
                subWindow.setHeight( "50%" );
                subWindow.setWidth( "50%" );
                subWindow.setEnabled( true );

                VerticalLayout subContent = new VerticalLayout();
                subContent.setSpacing( true );
                subContent.setMargin( true );

                HorizontalLayout scriptLayout = new HorizontalLayout();
                scriptLayout.setSpacing( true );
                scriptLayout.setWidth( "100%" );

                VerticalLayout scriptItems = new VerticalLayout();
                scriptItems.setSpacing( true );

                scriptItems.addComponent( uploadScript );
                scriptItems.addComponent( scriptCheck );

                addOperation = new Button( "Save" );
                addOperation.addStyleName( BUTTON_STYLE_NAME );
                addOperation.addClickListener( new Button.ClickListener()
                {
                    public void buttonClick( final Button.ClickEvent clickEvent )
                    {
                        if ( newName.getValue().isEmpty() )
                        {
                            show( "Please enter operation name" );
                        }
                        else if ( commandArea.getValue().isEmpty() )
                        {
                            show( "Please enter command" );
                        }
                        else
                        {
                            Profile current = ( Profile ) profileSelect.getValue();
                            boolean exist = false;
                            for ( final Operation operation : genericPlugin.getProfileOperations( current.getId() ) )
                            {
                                if ( newName.getValue().equals( operation.getOperationName() ) )
                                {
                                    show( "Operation with such name already exists" );
                                    exist = true;
                                }
                            }

                            if ( !exist )
                            {
                                genericPlugin
                                        .saveOperation( current.getId(), newName.getValue(), commandArea.getValue(),
                                                cwd.getValue(), timeOut.getValue(), daemon.getValue(), fromFile );
                                cleanTextBoxes();
                                show( "Operation saved successfully!" );
                                refreshUI();
                                subWindow.close();
                            }
                        }
                    }
                } );

                scriptLayout.addComponent( commandArea );
                scriptLayout.addComponent( scriptItems );
                scriptLayout.setComponentAlignment( scriptItems, Alignment.MIDDLE_LEFT );

                subContent.addComponent( newName );
                subContent.addComponent( cwd );
                subContent.addComponent( timeOut );
                subContent.addComponent( daemon );
                subContent.addComponent( scriptLayout );
                subContent.addComponent( addOperation );

                subWindow.setContent( subContent );
                UI.getCurrent().addWindow( subWindow );
            }
        } );

        uploadScript = new Button( "Upload script" );
        uploadScript.addStyleName( "default" );
        uploadScript.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent event )
            {
                uploadWindow = createWindowTemplate( "Upload Script" );

                VerticalLayout content = new VerticalLayout();
                content.setSpacing( true );
                content.setMargin( true );

                MyUploader uploader = new MyUploader();
                Upload upload = new Upload( "", uploader );
                upload.setButtonCaption( "Start Upload" );
                upload.addSucceededListener( uploader );
                upload.addFailedListener( uploader );

                Button cancel = new Button( "Cancel" );
                cancel.addClickListener( new Button.ClickListener()
                {
                    @Override
                    public void buttonClick( Button.ClickEvent event )
                    {
                        uploadWindow.close();
                    }
                } );

                content.addComponent( upload );
                content.addComponent( cancel );
                content.setComponentAlignment( upload, Alignment.BOTTOM_CENTER );
                content.setComponentAlignment( cancel, Alignment.BOTTOM_CENTER );
                uploadWindow.setContent( content );
                UI.getCurrent().addWindow( uploadWindow );
            }
        } );

        content.addComponent( title );
        content.addComponent( profileSelect );
        content.addComponent( fieldGrid );
        content.addComponent( showAddOperationWindow );
        content.addComponent( operationTable );
        content.setSpacing( true );

        Button back = new Button( "Back" );
        back.addStyleName( BUTTON_STYLE_NAME );
        back.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( final Button.ClickEvent clickEvent )
            {
                wizard.changeWindow( 0 );
                wizard.putForm();
            }
        } );
        content.addComponent( back );

        setContent( content );
    }


    private Table createTableTemplate( final String caption )
    {
        final Table table = new Table( caption );
        table.addContainerProperty( "Operation name", String.class, null );
        table.addContainerProperty( "Actions", HorizontalLayout.class, null );
        table.setWidth( "50%" );
        table.setPageLength( 10 );
        table.setSelectable( false );
        table.setImmediate( true );

        return table;
    }


    private Window createWindowTemplate( final String caption )
    {
        Window subWindow = new Window( caption );
        subWindow.setClosable( true );
        subWindow.setModal( true );
        subWindow.addStyleName( "default" );
        subWindow.center();

        return subWindow;
    }


    private void refreshUI()
    {
        if ( profile != null )
        {
            List<Operation> operations = genericPlugin.getProfileOperations( profile.getId() );
            populateTable( operationTable, operations );
        }
    }


    private void populateTable( final Table sampleTable, final List<Operation> operations )
    {
        sampleTable.removeAllItems();

        for ( Operation operation : operations )
        {
            final Button viewBtn = new Button( "View" );
            final Button editBtn = new Button( "Edit" );
            final Button deleteBtn = new Button( "Delete" );

            addStyleNameToButtons( viewBtn, editBtn, deleteBtn );

            final HorizontalLayout availableOperations = new HorizontalLayout();
            availableOperations.addStyleName( "default" );
            availableOperations.setSpacing( true );

            addGivenComponents( availableOperations, viewBtn, editBtn, deleteBtn );
            sampleTable.addItem( new Object[] { operation.getOperationName(), availableOperations }, null );

            addClickListenerToViewButton( viewBtn, operation );
            addClickListenerToEditButton( editBtn, operation );
            addClickListenerToDeleteButton( deleteBtn, operation );
        }
    }


    private void addClickListenerToDeleteButton( final Button deleteBtn, final Operation operation )
    {
        deleteBtn.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent event )
            {
                ConfirmationDialog alert = new ConfirmationDialog(
                        String.format( "Do you want to remove %s profile ?", profile.getName() ), "Yes", "No" );
                alert.getOk().addClickListener( new Button.ClickListener()
                {
                    @Override
                    public void buttonClick( Button.ClickEvent clickEvent )
                    {
                        genericPlugin.deleteOperation( operation.getOperationId() );
                        show( "Operation deleted successfully!" );
                        refreshUI();
                    }
                } );

                content.getUI().addWindow( alert.getAlert() );

            }
        } );
    }


    private void addClickListenerToViewButton( Button viewBtn, final Operation operation )
    {
        viewBtn.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent event )
            {
                Window subwindow = new ViewOperationWindow( operation );
                UI.getCurrent().addWindow( subwindow );
            }
        } );
    }


    private void addClickListenerToEditButton( Button editBtn, final Operation operation )
    {
        editBtn.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent event )
            {
                final Window subWindow = createWindowTemplate( "Operation info" );
                subWindow.setHeight( "50%" );
                subWindow.setWidth( "50%" );

                VerticalLayout subContent = new VerticalLayout();
                subContent.setSpacing( true );
                subContent.setMargin( true );

                HorizontalLayout editInfo = new HorizontalLayout();
                editInfo.setSpacing( true );
                editInfo.setWidth( "100%" );

                byte[] decodedBytes = Base64.decodeBase64( operation.getCommandName() );

                commandArea.setValue( new String( decodedBytes ) );
                cwd.setValue( operation.getCwd() );
                timeOut.setValue( operation.getTimeout() );
                daemon.setValue( operation.getDaemon() );

                HorizontalLayout buttons = new HorizontalLayout();
                buttons.setSpacing( true );
                buttons.setWidth( "100%" );

                Button cancel = new Button( "Cancel" );
                cancel.addStyleName( BUTTON_STYLE_NAME );
                cancel.addClickListener( new Button.ClickListener()
                {
                    @Override
                    public void buttonClick( Button.ClickEvent clickEvent )
                    {
                        subWindow.close();
                    }
                } );


                Button finalEdit = new Button( "Edit" );
                finalEdit.addStyleName( BUTTON_STYLE_NAME );
                finalEdit.addClickListener( new Button.ClickListener()
                {
                    @Override
                    public void buttonClick( Button.ClickEvent clickEvent )
                    {
                        if ( commandArea.getValue().isEmpty() )
                        {
                            Notification notif = new Notification( "Please enter command" );
                            notif.setDelayMsec( 2000 );
                            notif.show( Page.getCurrent() );
                        }
                        else
                        {
                            genericPlugin.updateOperation( operation.getOperationId(), commandArea.getValue(),
                                    cwd.getValue(), timeOut.getValue(), daemon.getValue(), fromFile );
                            show( "Operation saved successfully!" );
                            fromFile = false;
                            subWindow.close();
                            refreshUI();
                        }
                    }
                } );


                subContent.addComponent( cwd );
                subContent.addComponent( timeOut );
                subContent.addComponent( daemon );
                subContent.addComponent( commandArea );

                buttons.addComponent( cancel );
                buttons.addComponent( finalEdit );
                buttons.setComponentAlignment( cancel, Alignment.BOTTOM_CENTER );
                buttons.setComponentAlignment( finalEdit, Alignment.BOTTOM_CENTER );


                subContent.addComponent( buttons );


                subWindow.setContent( subContent );
                UI.getCurrent().addWindow( subWindow );
            }
        } );
    }


    private void refreshProfileInfo()
    {
        profileSelect.removeAllItems();
        List<Profile> profileInfo = genericPlugin.getProfiles();
        if ( profileInfo != null && !profileInfo.isEmpty() )
        {
            for ( Profile pi : profileInfo )
            {
                profileSelect.addItem( pi );
                profileSelect.setItemCaption( pi, pi.getName() );
            }

            Long currentProfileId = wizard.getCurrentProfileId();
            if ( currentProfileId != null )
            {
                for ( final Profile pi : profileInfo )
                {
                    if ( pi.getId().equals( currentProfileId ) )
                    {
                        profileSelect.setValue( pi );
                        profileSelect.setItemCaption( pi, pi.getName() );
                    }
                }
            }
            else
            {
                Profile temp = profileInfo.get( 0 );
                profileSelect.setValue( temp );
                profileSelect.setItemCaption( temp, temp.getName() );
            }
        }
    }


    private void addStyleNameToButtons( Button... buttons )
    {
        for ( Button b : buttons )
        {
            b.addStyleName( BUTTON_STYLE_NAME );
        }
    }


    private void addGivenComponents( HorizontalLayout layout, Button... buttons )
    {
        for ( Button b : buttons )
        {
            layout.addComponent( b );
        }
    }


    private void show( String notification )
    {
        Notification notif = new Notification( notification );
        notif.setDelayMsec( 2000 );
        notif.show( Page.getCurrent() );
    }


    private void cleanTextBoxes()
    {
        newName.setValue( "" );
        commandArea.setValue( "" );
        scriptCheck.setRequired( false );
    }
}
