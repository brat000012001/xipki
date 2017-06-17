/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.common;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.xipki.common.util.ParamUtil;
import org.xipki.common.util.StringUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class LoadExecutor {

    private static final String PROPKEY_LOADTEST = "org.xipki.loadtest";

    private static final int DEFAULT_DURATION = 30; // 30 seconds

    private static final int DEFAULT_THREADS = 25;

    private boolean interrupted;

    private String description;

    private final ProcessLog processLog;

    private int duration = DEFAULT_DURATION; // in seconds

    private int threads = DEFAULT_THREADS;

    private AtomicLong errorAccount = new AtomicLong(0);

    private String unit = "";

    public LoadExecutor(final String description) {
        this.description = ParamUtil.requireNonNull("description", description);
        this.processLog = new ProcessLog(0);
    }

    protected abstract Runnable getTestor() throws Exception;

    protected void shutdown() {
    }

    public void test() {
        System.getProperties().setProperty(PROPKEY_LOADTEST, "true");
        List<Runnable> runnables = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            Runnable runnable;
            try {
                runnable = getTestor();
            } catch (Exception ex) {
                System.err.println("could not initialize Testor: " + ex.getMessage());
                return;
            }

            runnables.add(runnable);
        }

        StringBuilder sb = new StringBuilder();
        if (StringUtil.isNotBlank(description)) {
            sb.append(description);
            char ch = description.charAt(description.length() - 1);
            if (ch != '\n') {
                sb.append('\n');
            }
        }
        sb.append("threads: ").append(threads).append("\n");
        sb.append("duration: ").append(StringUtil.formatTime(duration, false));
        System.out.println(sb.toString());

        resetStartTime();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (Runnable runnable : runnables) {
            executor.execute(runnable);
        }

        executor.shutdown();
        printHeader();
        while (true) {
            printStatus();
            try {
                boolean terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
                if (terminated) {
                    break;
                }
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }

        printStatus();
        printSummary();

        shutdown();
        System.getProperties().remove(PROPKEY_LOADTEST);
    } // method test

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setDuration(final String duration) {
        ParamUtil.requireNonBlank("duration", duration);
        char unit = duration.charAt(duration.length() - 1);

        String numStr;
        if (unit == 's' || unit == 'm' || unit == 'h') {
            numStr = duration.substring(0, duration.length() - 1);
        } else {
            unit = 's';
            numStr = duration;
        }

        int num;
        try {
            num = Integer.parseInt(numStr);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid duration " + duration);
        }

        if (num < 1) {
            throw new IllegalArgumentException("invalid duration " + duration);
        }

        switch (unit) {
        case 's':
            this.duration = num;
            break;
        case 'm':
            this.duration = num * 60;
            break;
        case 'h':
            this.duration = num * 60 * 24;
            break;
        default:
            throw new RuntimeException("invalid duration unit " + unit);
        }
    }

    public void setThreads(final int threads) {
        if (threads > 0) {
            this.threads = threads;
        }
    }

    public long getErrorAccout() {
        return errorAccount.get();
    }

    public void account(final int all, final int failed) {
        processLog.addNumProcessed(all);
        errorAccount.addAndGet(failed);
    }

    protected void resetStartTime() {
        processLog.reset();
    }

    protected boolean stop() {
        return interrupted
                || errorAccount.get() > 0
                || System.currentTimeMillis() - processLog.startTime() >= duration * 1000L;
    }

    protected void printHeader() {
        processLog.printHeader();
    }

    protected void printStatus() {
        processLog.printStatus();
    }

    public void setUnit(final String unit) {
        this.unit = ParamUtil.requireNonNull("unit", unit);
    }

    protected void printSummary() {
        processLog.printTrailer();

        final long account = processLog.numProcessed();
        StringBuilder sb = new StringBuilder();
        long elapsedTimeMs = processLog.totalElapsedTime();
        sb.append("finished in ")
            .append(StringUtil.formatTime(elapsedTimeMs / 1000, false)).append("\n")
            .append("account: ")
            .append(StringUtil.formatAccount(account, 1))
            .append(" ")
            .append(unit).append("\n")
            .append(" failed: ")
            .append(StringUtil.formatAccount(errorAccount.get(), 1))
            .append(" ")
            .append(unit).append("\n")
            .append("average: ")
            .append(StringUtil.formatAccount(processLog.totalAverageSpeed(), 1))
            .append(" ")
            .append(unit)
            .append("/s\n");

        System.out.println(sb.toString());
    }

    protected static long getSecureIndex() {
        SecureRandom random = new SecureRandom();
        while (true) {
            long nextLong = random.nextLong();
            if (nextLong > 0) {
                return nextLong;
            }
        }
    }

}
