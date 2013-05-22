/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel;

import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.Settings;
import org.neo4j.kernel.guard.Guard;
import org.neo4j.kernel.guard.GuardOperationsCountException;
import org.neo4j.kernel.guard.GuardTimeoutException;
import org.neo4j.test.TestGraphDatabaseFactory;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Thread.sleep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.neo4j.graphdb.DynamicRelationshipType.withName;
import static org.neo4j.helpers.SillyUtils.ignore;

@SuppressWarnings("deprecation"/*getGuard() is deprecated (GraphDatabaseAPI), and used all throughout this test*/)
public class TestGuard
{
    @Test
    public void testGuardNotInsertedByDefault()
    {
        GraphDatabaseAPI db = (GraphDatabaseAPI) new TestGraphDatabaseFactory().newImpermanentDatabase();
        assertNull( db.getGuard() );
        db.shutdown();
    }

    @Test
    public void testGuardInsertedByDefault()
    {
        GraphDatabaseAPI db = (GraphDatabaseAPI) new TestGraphDatabaseFactory().
            newImpermanentDatabaseBuilder().
            setConfig( GraphDatabaseSettings.execution_guard_enabled, Settings.TRUE ).
            newGraphDatabase();
        assertNotNull( db.getGuard() );
        db.shutdown();
    }

    @Test
    public void testGuardOnDifferentGraphOps()
    {
        GraphDatabaseAPI db = (GraphDatabaseAPI) new TestGraphDatabaseFactory().
            newImpermanentDatabaseBuilder().
            setConfig( GraphDatabaseSettings.execution_guard_enabled, Settings.TRUE ).
            newGraphDatabase();
        db.beginTx();

        db.getGuard().startOperationsCount( MAX_VALUE );
        db.createNode();
        db.createNode();
        db.createNode();
        Guard.OperationsCount ops1 = db.getGuard().stop();
        assertEquals( 3, ops1.getOpsCount() );

        db.getGuard().startOperationsCount( MAX_VALUE );
        Node n0 = db.getNodeById( 0 );
        Node n1 = db.getNodeById( 1 );
        Node n2 = db.getNodeById( 2 );
        Node n3 = db.getNodeById( 3 );
        Guard.OperationsCount ops2 = db.getGuard().stop();
        assertEquals( 4, ops2.getOpsCount() );

        db.getGuard().startOperationsCount( MAX_VALUE );
        n0.createRelationshipTo( n1, withName( "REL" ));
        Guard.OperationsCount ops3 = db.getGuard().stop();
        assertEquals( 2, ops3.getOpsCount() );


        db.getGuard().startOperationsCount( MAX_VALUE );
        for ( Path position : Traversal.description().breadthFirst().relationships( withName( "REL" ) ).traverse( n0 ) )
        {
            ignore( position );
        }
        Guard.OperationsCount ops4 = db.getGuard().stop();
        assertEquals( 3, ops4.getOpsCount() );
        db.shutdown();
    }

    @Test
    public void testOpsCountGuardFail()
    {
        GraphDatabaseAPI db = (GraphDatabaseAPI) new TestGraphDatabaseFactory().
            newImpermanentDatabaseBuilder().
            setConfig( GraphDatabaseSettings.execution_guard_enabled, Settings.TRUE ).
            newGraphDatabase();
        db.beginTx();
        Guard guard = db.getGuard();

        guard.startOperationsCount( 2 );
        Node n0 = db.getNodeById( 0 );
        Node n1 = db.createNode();
        try
        {
            Node n2 = db.createNode();
            fail();
        } catch ( GuardOperationsCountException e )
        {
            // expected
        }
        db.shutdown();
    }

    @Test
    public void testTimeoutGuardFail() throws InterruptedException
    {
        GraphDatabaseAPI db = (GraphDatabaseAPI) new TestGraphDatabaseFactory().
                newImpermanentDatabaseBuilder().
                setConfig( GraphDatabaseSettings.execution_guard_enabled, Settings.TRUE ).
                newGraphDatabase();
        db.beginTx();

        db.getDependencyResolver().resolveDependency( Guard.class ).startTimeout( 50 );
        sleep( 100 );

        try
        {
            db.createNode();
            fail( "Expected guard to stop this" );
        }
        catch ( GuardTimeoutException e )
        {
            // expected
        }
        db.shutdown();
    }

    @Test
    public void testTimeoutGuardPass()
    {
        GraphDatabaseAPI db = (GraphDatabaseAPI) new TestGraphDatabaseFactory().
                newImpermanentDatabaseBuilder().
                setConfig( GraphDatabaseSettings.execution_guard_enabled, Settings.TRUE ).
                newGraphDatabase();
        db.beginTx();

        int timeout = 1000;
        db.getGuard().startTimeout( timeout );

        db.createNode(); // This should not throw
        db.shutdown();
    }
}
