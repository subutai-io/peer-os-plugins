package org.safehaus.subutai.plugin.hipi.rest;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.safehaus.subutai.common.tracker.OperationState;
import org.safehaus.subutai.common.tracker.TrackerOperationView;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.hipi.api.Hipi;
import org.safehaus.subutai.plugin.hipi.api.HipiConfig;
import org.safehaus.subutai.plugin.hipi.rest.RestServiceImpl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class RestServiceTest
{
    private RestServiceImpl restService;
    private HipiConfig hipiConfig;
    @Mock
    Hipi hipi;
    @Mock
    Tracker tracker;
    @Mock
    TrackerOperationView trackerOperationView;

    @Before
    public void setUp() throws Exception
    {
        hipiConfig = new HipiConfig();
        restService = new RestServiceImpl( hipi );
        restService.setTracker( tracker );
        when( hipi.getCluster( anyString() )).thenReturn( hipiConfig );
        when( tracker.getTrackerOperation( anyString(), any( UUID.class) ) ).thenReturn( trackerOperationView );
        when( trackerOperationView.getState() ).thenReturn( OperationState.SUCCEEDED );
    }


    @Test
    public void testListClusters() throws Exception
    {
        List<HipiConfig> myList = new ArrayList<>();
        myList.add( hipiConfig );
        when( hipi.getClusters() ).thenReturn( myList );

        Response response = restService.listClusters();

        // assertions
        assertEquals( Response.Status.OK.getStatusCode(), response.getStatus() );
    }


    @Test
    public void testGetCluster() throws Exception
    {
        Response response = restService.getCluster( "test" );

        // assertions
        assertEquals( Response.Status.OK.getStatusCode(), response.getStatus() );
    }


    @Test
    public void testInstallCluster() throws Exception
    {
        Response response = restService.installCluster( "test", "test", UUID.randomUUID().toString() );

        // assertions
        assertEquals( Response.Status.OK.getStatusCode(), response.getStatus() );

    }


    @Test
    public void testUninstallCluster() throws Exception
    {
        Response response = restService.uninstallCluster( "test" );

        // assertions
        assertEquals( Response.Status.OK.getStatusCode(), response.getStatus() );
    }


    @Test
    public void testAddNode() throws Exception
    {
        Response response = restService.addNode( "testClusterName", "testLxcHostName" );

        // assertions
        assertEquals( Response.Status.OK.getStatusCode(), response.getStatus() );
    }


    @Test
    public void testDestroyNode() throws Exception
    {
        Response response = restService.destroyNode( "testClusterName", "testLxcHostName" );

        // assertions
        assertEquals( Response.Status.OK.getStatusCode(), response.getStatus() );
    }
}