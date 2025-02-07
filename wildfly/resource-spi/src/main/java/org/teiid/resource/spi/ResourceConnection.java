package org.teiid.resource.spi;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionMetaData;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.LocalTransaction;
import jakarta.resource.cci.ResultSetInfo;
import jakarta.resource.spi.ManagedConnection;
import javax.transaction.xa.XAResource;

public interface ResourceConnection extends Connection, org.teiid.resource.api.Connection {

    @Override
    default Interaction createInteraction() throws ResourceException {
        throw new ResourceException("This operation not supported"); //$NON-NLS-1$
    }

    @Override
    default LocalTransaction getLocalTransaction() throws ResourceException {
        throw new NotSupportedException();
    }

    @Override
    default ConnectionMetaData getMetaData() throws ResourceException {
        throw new ResourceException("This operation not supported"); //$NON-NLS-1$
    }

    @Override
    default ResultSetInfo getResultSetInfo() throws ResourceException {
        throw new ResourceException("This operation not supported"); //$NON-NLS-1$
    }

    /**
     * Supply the {@link XAResource} is applicable to the {@link BasicManagedConnection}
     * @return
     * @throws ResourceException
     */
    default XAResource getXAResource() throws ResourceException {
        return null;
    }

    /**
     * Tests the connection to see if it is still valid.
     * @return
     */
    default boolean isAlive() {
        return true;
    }

    /**
     * Called by the {@link ManagedConnection} to indicate the physical connection
     * should be cleaned up for reuse.
     */
    default void cleanUp() {

    }

}
