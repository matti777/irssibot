/*
 Copyright  2007 MySQL AB, 2008 Sun Microsystems

 This program is free software; you can redistribute it and/or modify
 it under the terms of version 2 of the GNU General Public License as 
 published by the Free Software Foundation.

 There are special exceptions to the terms and conditions of the GPL 
 as it is applied to this software. View the full text of the 
 exception in file EXCEPTIONS-CONNECTOR-J in the directory of this 
 software distribution.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 
 */
package com.mysql.jdbc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * A RowHolder implementation that is for cached results (a-la
 * mysql_store_result()).
 * 
 * @version $Id: $
 */
public class ByteArrayRow extends ResultSetRow {

	byte[][] internalRowData;

	public ByteArrayRow(byte[][] internalRowData, ExceptionInterceptor exceptionInterceptor) {
		super(exceptionInterceptor);
		
		this.internalRowData = internalRowData;
	}

	public byte[] getColumnValue(int index) throws SQLException {
		return this.internalRowData[index];
	}

	public void setColumnValue(int index, byte[] value) throws SQLException {
		this.internalRowData[index] = value;
	}

	public String getString(int index, String encoding, ConnectionImpl conn)
			throws SQLException {
		byte[] columnData = this.internalRowData[index];

		if (columnData == null) {
			return null;
		}

		return getString(encoding, conn, columnData, 0, columnData.length);
	}

	public boolean isNull(int index) throws SQLException {
		return this.internalRowData[index] == null;
	}

	public boolean isFloatingPointNumber(int index) throws SQLException {
		byte[] numAsBytes = this.internalRowData[index];

		if (this.internalRowData[index] == null
				|| this.internalRowData[index].length == 0) {
			return false;
		}

		for (int i = 0; i < numAsBytes.length; i++) {
			if (((char) numAsBytes[i] == 'e') || ((char) numAsBytes[i] == 'E')) {
				return true;
			}
		}

		return false;
	}

	public long length(int index) throws SQLException {
		if (this.internalRowData[index] == null) {
			return 0;
		}

		return this.internalRowData[index].length;
	}

	public int getInt(int columnIndex) {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return StringUtils.getInt(this.internalRowData[columnIndex]);
	}

	public long getLong(int columnIndex) {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return StringUtils.getLong(this.internalRowData[columnIndex]);
	}

	public Timestamp getTimestampFast(int columnIndex, Calendar targetCalendar,
			TimeZone tz, boolean rollForward, ConnectionImpl conn,
			ResultSetImpl rs) throws SQLException {
		byte[] columnValue = this.internalRowData[columnIndex];

		if (columnValue == null) {
			return null;
		}

		return getTimestampFast(columnIndex, this.internalRowData[columnIndex],
				0, columnValue.length, targetCalendar, tz, rollForward, conn,
				rs);
	}

	public double getNativeDouble(int columnIndex) throws SQLException {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return getNativeDouble(this.internalRowData[columnIndex], 0);
	}

	public float getNativeFloat(int columnIndex) throws SQLException {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return getNativeFloat(this.internalRowData[columnIndex], 0);
	}

	public int getNativeInt(int columnIndex) throws SQLException {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return getNativeInt(this.internalRowData[columnIndex], 0);
	}

	public long getNativeLong(int columnIndex) throws SQLException {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return getNativeLong(this.internalRowData[columnIndex], 0);
	}

	public short getNativeShort(int columnIndex) throws SQLException {
		if (this.internalRowData[columnIndex] == null) {
			return 0;
		}

		return getNativeShort(this.internalRowData[columnIndex], 0);
	}

	public Timestamp getNativeTimestamp(int columnIndex,
			Calendar targetCalendar, TimeZone tz, boolean rollForward,
			ConnectionImpl conn, ResultSetImpl rs) throws SQLException {
		byte[] bits = this.internalRowData[columnIndex];

		if (bits == null) {
			return null;
		}

		return getNativeTimestamp(bits, 0, bits.length, targetCalendar, tz,
				rollForward, conn, rs);
	}

	public void closeOpenStreams() {
		// no-op for this type
	}

	public InputStream getBinaryInputStream(int columnIndex)
			throws SQLException {
		if (this.internalRowData[columnIndex] == null) {
			return null;
		}

		return new ByteArrayInputStream(this.internalRowData[columnIndex]);
	}

	public Reader getReader(int columnIndex) throws SQLException {
		InputStream stream = getBinaryInputStream(columnIndex);

		if (stream == null) {
			return null;
		}

		try {
			return new InputStreamReader(stream, this.metadata[columnIndex]
					.getCharacterSet());
		} catch (UnsupportedEncodingException e) {
			SQLException sqlEx = SQLError.createSQLException("", this.exceptionInterceptor);

			sqlEx.initCause(e);

			throw sqlEx;
		}
	}

	public Time getTimeFast(int columnIndex, Calendar targetCalendar,
			TimeZone tz, boolean rollForward, ConnectionImpl conn,
			ResultSetImpl rs) throws SQLException {
		byte[] columnValue = this.internalRowData[columnIndex];

		if (columnValue == null) {
			return null;
		}

		return getTimeFast(columnIndex, this.internalRowData[columnIndex], 0,
				columnValue.length, targetCalendar, tz, rollForward, conn, rs);
	}

	public Date getDateFast(int columnIndex, ConnectionImpl conn,
			ResultSetImpl rs, Calendar targetCalendar) throws SQLException {
		byte[] columnValue = this.internalRowData[columnIndex];

		if (columnValue == null) {
			return null;
		}

		return getDateFast(columnIndex, this.internalRowData[columnIndex], 0,
				columnValue.length, conn, rs, targetCalendar);
	}

	public Object getNativeDateTimeValue(int columnIndex, Calendar targetCalendar,
			int jdbcType, int mysqlType, TimeZone tz,
			boolean rollForward, ConnectionImpl conn, ResultSetImpl rs)
			throws SQLException {
		byte[] columnValue = this.internalRowData[columnIndex];

		if (columnValue == null) {
			return null;
		}

		return getNativeDateTimeValue(columnIndex, columnValue, 0,
				columnValue.length, targetCalendar, jdbcType, mysqlType, tz,
				rollForward, conn, rs);
	}

	public Date getNativeDate(int columnIndex, ConnectionImpl conn,
			ResultSetImpl rs) throws SQLException {
		byte[] columnValue = this.internalRowData[columnIndex];

		if (columnValue == null) {
			return null;
		}

		return getNativeDate(columnIndex, columnValue, 0, columnValue.length,
				conn, rs);
	}

	public Time getNativeTime(int columnIndex, Calendar targetCalendar,
			TimeZone tz, boolean rollForward, ConnectionImpl conn,
			ResultSetImpl rs) throws SQLException {
		byte[] columnValue = this.internalRowData[columnIndex];

		if (columnValue == null) {
			return null;
		}

		return getNativeTime(columnIndex, columnValue, 0, columnValue.length,
				targetCalendar, tz, rollForward, conn, rs);
	}
}