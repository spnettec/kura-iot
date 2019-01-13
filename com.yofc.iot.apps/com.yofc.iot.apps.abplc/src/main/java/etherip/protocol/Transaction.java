/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

/**
 * Almost unique transaction ID
 *
 * <p>
 * Rolls around from 1 to 0xFFFFFFFF
 *
 * <p>
 * Used in the encapsulation header's `context`
 * thanks to John Priller who found that it can
 * detect a mismatched request/response
 * which he occasionally observed on a heavily loaded network.
 *
 * @author Kay Kasemir
 */
public class Transaction {

    private Transaction() {
        throw new IllegalStateException("Transaction class");
    }

    // SYNC on access
    private static long transactionNum;

    static long nextTransaction() {
        synchronized (Transaction.class) {
            ++transactionNum;
            if (transactionNum > 0xffffffffL)
                transactionNum = 1;
            return transactionNum;
        }
    }

    public static byte[] format(final long transaction) {
        return String.format("%08X", transaction).getBytes();
    }

}
