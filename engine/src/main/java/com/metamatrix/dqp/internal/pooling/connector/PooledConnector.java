/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.dqp.internal.pooling.connector;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;

import com.metamatrix.connector.api.Connection;
import com.metamatrix.connector.api.Connector;
import com.metamatrix.connector.api.ConnectorEnvironment;
import com.metamatrix.connector.api.ConnectorException;
import com.metamatrix.connector.api.ExecutionContext;
import com.metamatrix.connector.xa.api.TransactionContext;
import com.metamatrix.connector.xa.api.XAConnection;
import com.metamatrix.connector.xa.api.XAConnector;
import com.metamatrix.dqp.internal.datamgr.impl.ConnectorWrapper;

/**
 * Implements Pooling around a Connector or XAConnector.
 * 
 * XA Considerations:
 * 1. Two pools are maintained, one for connections participating in XA, and one for not.
 *    Most JDBC sources need to segregate, so this is the default.  TODO: make this configurable.
 * 2. XAConnections are bound to their transaction.  TODO: make this configurable.
 * 
 */
public class PooledConnector extends ConnectorWrapper {
	
	private final class RemovalCallback implements Synchronization {

		private final TransactionContext transactionContext;
		private final ConnectionWrapper conn;

		/**
		 * @param transactionContext
		 */
		private RemovalCallback(TransactionContext transactionContext,
				ConnectionWrapper conn) {
			this.transactionContext = transactionContext;
			this.conn = conn;
		}

		public void afterCompletion(int arg0) {
			synchronized (idToConnections) {
				idToConnections.remove(this.transactionContext.getTxnID());
				conn.setInTxn(false);
				if (!conn.isLeased()) {
					conn.close();
				}
			}
			if (environment.getLogger().isTraceEnabled()) {
				environment.getLogger().logTrace("released connection for transaction " + transactionContext.getTxnID()); //$NON-NLS-1$
			}
		}

		public void beforeCompletion() {
		}
	}

	private ConnectionPool pool;
	private ConnectionPool xaPool;
	private Map<String, ConnectionWrapper> idToConnections = Collections.synchronizedMap(new HashMap<String, ConnectionWrapper>());
	private ConnectorEnvironment environment;
	
	public PooledConnector(Connector actualConnector) {
		super(actualConnector);
		pool = new ConnectionPool(this);
		
		if (actualConnector instanceof XAConnector) {
			xaPool = new ConnectionPool(this);
		}
	}

	@Override
	public void start(ConnectorEnvironment environment)
			throws ConnectorException {
		Properties p = environment.getProperties();
		pool.initialize(p);
		if (xaPool != null) {
			xaPool.initialize(p);
		}
		super.start(environment);
	}
	
	@Override
	public void stop() {
		pool.shutDown();
		if (xaPool != null) {
			xaPool.shutDown();
		}
		super.stop();
	}

	@Override
	public Connection getConnectionDirect(ExecutionContext context)
			throws ConnectorException {
		return pool.obtain(context);
	}

	@Override
	public XAConnection getXAConnectionDirect(ExecutionContext executionContext,
			TransactionContext transactionContext) throws ConnectorException {
        ConnectionWrapper conn = null;
        
        if(transactionContext != null){
        	synchronized (idToConnections) {
                conn  = idToConnections.get(transactionContext.getTxnID());
                if (conn != null){
                	if (environment.getLogger().isTraceEnabled()) {
                		environment.getLogger().logTrace("Transaction " + transactionContext.getTxnID() + " already has connection, using the same connection"); //$NON-NLS-1$ //$NON-NLS-2$
                	}
                    conn.lease();
                    return conn;
                }
			}
        }
    
        conn = xaPool.obtain(executionContext, transactionContext, true);
        conn.lease();
        if (transactionContext != null) {
        	if (environment.getLogger().isTraceEnabled()) {
        		environment.getLogger().logTrace("Obtained new connection for transaction " + transactionContext.getTxnID()); //$NON-NLS-1$
        	}
            
            try { //add a synchronization to remove the map entry
                transactionContext.getTransaction().registerSynchronization(new RemovalCallback(transactionContext, conn));
            } catch (RollbackException err) {
                conn.close();
                throw new ConnectorException(err);
            } catch (SystemException err) {
                conn.close();
                throw new ConnectorException(err);
            }
	        conn.setInTxn(true);
	        synchronized (idToConnections) {
	            idToConnections.put(transactionContext.getTxnID(), conn);
			}
        }
        return conn;
	}

}
