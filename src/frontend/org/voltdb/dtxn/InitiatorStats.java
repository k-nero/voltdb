/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.dtxn;

import org.voltdb.VoltDB;
import org.voltdb.SiteStatsSource;
import org.voltdb.StoredProcedureInvocation;
import org.voltdb.SysProcSelector;
import org.voltdb.VoltType;
import org.voltdb.VoltTable.ColumnInfo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;

/**
 * Class that provides storage for statistical information generated by an Initiator
 */
public class InitiatorStats extends SiteStatsSource {


    /**
     * Map from the combined key of ConnectionId and ProcedureName to statistical information related to the specific procedure.
     */
    private final HashMap<String, InvocationInfo> m_connectionStats =
                new HashMap<String, InvocationInfo>();

    /**
     *
     * @param name
     * @param siteId
     */
    public InitiatorStats(String name, int siteId) {
        super(name, siteId);
        VoltDB.instance().getStatsAgent().registerStatsSource(SysProcSelector.INITIATOR, 0, this);
    }

    private static class InvocationInfo {

        /**
         * Hostname of the host this connection is with
         */
        private final String connectionHostname;

        /**
         * Number of time procedure has been invoked
         */
        private long invocationCount = 0;

        /**
         * Shortest amount of time this procedure has executed in
         */
        private int minExecutionTime = Integer.MAX_VALUE;

        /**
         * Longest amount of time this procedure has executed in
         */
        private int maxExecutionTime = Integer.MIN_VALUE;

        /**
         * Total amount of time spent executing procedure
         */
        private long totalExecutionTime = 0;

        public InvocationInfo (String hostname) {
            connectionHostname = hostname;
        }

        private void processInvocation(int delta) {
            totalExecutionTime += delta;
            minExecutionTime = Math.min( delta, minExecutionTime);
            maxExecutionTime = Math.max(  delta, maxExecutionTime);
            invocationCount++;
        }
    }
    /**
     * Called by the Initiator every time a transaction is completed
     * @param connectionId Id of the connection that the invocation orginated from
     * @param invocation Procedure executed
     * @param delta Time the procedure took to round trip intra cluster
     */
    public synchronized void logTransactionCompleted(
            int connectionId,
            String connectionHostname,
            StoredProcedureInvocation invocation,
            int delta) {
        final StringBuffer key = new StringBuffer(2048);
        key.append(invocation.getProcName()).append('$').append(connectionId);
        final String keyString = key.toString();
        InvocationInfo info = m_connectionStats.get(keyString);
        if (info == null) {
            info = new InvocationInfo(connectionHostname);
            m_connectionStats.put(keyString, info);
        }
        info.processInvocation(delta);
    }

    @Override
    protected void populateColumnSchema(ArrayList<ColumnInfo> columns) {
        super.populateColumnSchema(columns);
        columns.add(new ColumnInfo("CONNECTION_ID", VoltType.INTEGER));
        columns.add(new ColumnInfo("CONNECTION_HOSTNAME", VoltType.STRING));
        columns.add(new ColumnInfo("PROCEDURE_NAME", VoltType.STRING));
        columns.add(new ColumnInfo("INVOCATIONS", VoltType.BIGINT));
        columns.add(new ColumnInfo("AVG_EXECUTION_TIME", VoltType.INTEGER));
        columns.add(new ColumnInfo("MIN_EXECUTION_TIME", VoltType.INTEGER));
        columns.add(new ColumnInfo("MAX_EXECUTION_TIME", VoltType.INTEGER));
        columns.add(new ColumnInfo("TOTAL_EXECUTION_TIME", VoltType.BIGINT));
    }

    @Override
    protected void updateStatsRow(final Object rowKey, Object rowValues[]) {
        InvocationInfo info = m_connectionStats.get(rowKey);
        final String statsKey = (String)rowKey;
        final String statsKeySplit[] = statsKey.split("\\$");
        final String procName = statsKeySplit[0];
        final String connectionId = statsKeySplit[1];
        rowValues[columnNameToIndex.get("CONNECTION_ID")] = new Integer(connectionId);
        rowValues[columnNameToIndex.get("CONNECTION_HOSTNAME")] = info.connectionHostname;
        rowValues[columnNameToIndex.get("PROCEDURE_NAME")] = procName;
        rowValues[columnNameToIndex.get("INVOCATIONS")] = info.invocationCount;
        rowValues[columnNameToIndex.get("AVG_EXECUTION_TIME")] = (int)(info.totalExecutionTime / info.invocationCount);
        rowValues[columnNameToIndex.get("MIN_EXECUTION_TIME")] = info.minExecutionTime;
        rowValues[columnNameToIndex.get("MAX_EXECUTION_TIME")] = info.maxExecutionTime;
        rowValues[columnNameToIndex.get("TOTAL_EXECUTION_TIME")] = info.totalExecutionTime;
        super.updateStatsRow(rowKey, rowValues);
    }

    /**
     * A dummy iterator that wraps an Iterator<String> and provides the Iterator<Object> necessary
     * for getStatsRowKeyIterator()
     *
     */
    private class DummyIterator implements Iterator<Object> {
        private final Iterator<String> i;

        private DummyIterator(Iterator<String> i) {
            this.i = i;
        }

        @Override
        public boolean hasNext() {
            return i.hasNext();
        }

        @Override
        public Object next() {
            return i.next();
        }

        @Override
        public void remove() {
            i.remove();
        }


    }

    @Override
    protected Iterator<Object> getStatsRowKeyIterator() {
        return new DummyIterator(m_connectionStats.keySet().iterator());
    }

    @Override
    public synchronized void reset() {
        m_connectionStats.clear();
    }

}
