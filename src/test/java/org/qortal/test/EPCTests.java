package org.qortal.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.qortal.utils.ExecuteProduceConsume;

public class EPCTests {

	class RandomEPC extends ExecuteProduceConsume {
		private final int TASK_PERCENT;
		private final int PAUSE_PERCENT;

		public RandomEPC(ExecutorService executor, int taskPercent, int pausePercent) {
			super(executor);

			this.TASK_PERCENT = taskPercent;
			this.PAUSE_PERCENT = pausePercent;
		}

		@Override
		protected Task produceTask(boolean canIdle) throws InterruptedException {
			if (Thread.interrupted())
				throw new InterruptedException();

			Random random = new Random();

			final int percent = random.nextInt(100);

			// Sometimes produce a task
			if (percent < TASK_PERCENT) {
				return () -> {
					Thread.sleep(random.nextInt(500) + 100);
				};
			} else {
				// If we don't produce a task, then maybe simulate a pause until work arrives
				if (canIdle && percent < PAUSE_PERCENT)
					Thread.sleep(random.nextInt(100));

				return null;
			}
		}
	}

	private void testEPC(ExecuteProduceConsume testEPC) throws InterruptedException {
		final int runTime = 60; // seconds
		System.out.println(String.format("Testing EPC for %s seconds:", runTime));

		final long start = System.currentTimeMillis();
		testEPC.start();

		// Status reports every second (bar waiting for synchronization)
		ScheduledExecutorService statusExecutor = Executors.newSingleThreadScheduledExecutor();

		statusExecutor.scheduleAtFixedRate(() -> {
			synchronized (testEPC) {
				final long seconds = (System.currentTimeMillis() - start) / 1000L;
				System.out.println(String.format("After %d second%s, active threads: %d, greatest thread count: %d, tasks produced: %d, tasks consumed: %d",
						seconds, (seconds != 1 ? "s" : ""),
						testEPC.getActiveThreadCount(), testEPC.getGreatestActiveThreadCount(),
						testEPC.getTasksProduced(), testEPC.getTasksConsumed()));
			}
		}, 1L, 1L, TimeUnit.SECONDS);

		// Let it run for a minute
		Thread.sleep(runTime * 1000L);
		statusExecutor.shutdownNow();

		final long before = System.currentTimeMillis();
		testEPC.shutdown(30 * 1000);
		final long after = System.currentTimeMillis();

		System.out.println(String.format("Shutdown took %d milliseconds", after - before));
		System.out.println(String.format("Greatest thread count: %d", testEPC.getGreatestActiveThreadCount()));

		System.out.println(String.format("Tasks produced: %d", testEPC.getTasksProduced()));
		System.out.println(String.format("Tasks consumed: %d", testEPC.getTasksConsumed()));
	}

	@Test
	public void testRandomEPC() throws InterruptedException {
		final int TASK_PERCENT = 25; // Produce a task this % of the time
		final int PAUSE_PERCENT = 80; // Pause for new work this % of the time

		final ExecutorService executor = Executors.newCachedThreadPool();

		testEPC(new RandomEPC(executor, TASK_PERCENT, PAUSE_PERCENT));
	}

	@Test
	public void testRandomFixedPoolEPC() throws InterruptedException {
		final int TASK_PERCENT = 25; // Produce a task this % of the time
		final int PAUSE_PERCENT = 80; // Pause for new work this % of the time
		final int MAX_THREADS = 3;

		final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

		testEPC(new RandomEPC(executor, TASK_PERCENT, PAUSE_PERCENT));
	}

	/**
	 * Test ping scenario with many peers requiring pings.
	 * <p>
	 * Specifically, if:
	 * <ul>
	 * <li>the idling EPC thread sleeps for 1 second</li>
	 * <li>pings are required every P seconds</li>
	 * <li>there are way more than P peers</li>
	 * </ul>
	 * then we need to make sure EPC threads are not
	 * delayed such that some peers (>P) don't get a
	 * chance to be pinged.
	 */
	@Test
	public void testPingEPC() throws InterruptedException {
		final long PRODUCER_SLEEP_TIME = 1000; // ms
		final long PING_INTERVAL = PRODUCER_SLEEP_TIME * 8; // ms
		final long PING_ROUND_TRIP_TIME = PRODUCER_SLEEP_TIME * 5; // ms

		final int MAX_PEERS = 20;

		final List<Long> lastPings = new ArrayList<>(Collections.nCopies(MAX_PEERS, System.currentTimeMillis()));

		class PingTask implements ExecuteProduceConsume.Task {
			private final int peerIndex;

			public PingTask(int peerIndex) {
				this.peerIndex = peerIndex;
			}

			@Override
			public void perform() throws InterruptedException {
				System.out.println("Pinging peer " + peerIndex);

				// At least half the worst case ping round-trip
				Random random = new Random();
				int halfTime = (int) PING_ROUND_TRIP_TIME / 2;
				long sleep = random.nextInt(halfTime) + halfTime;
				Thread.sleep(sleep);
			}
		}

		class PingEPC extends ExecuteProduceConsume {
			@Override
			protected Task produceTask(boolean canIdle) throws InterruptedException {
				// If we can idle, then we do, to simulate worst case
				if (canIdle)
					Thread.sleep(PRODUCER_SLEEP_TIME);

				// Is there a peer that needs a ping?
				final long now = System.currentTimeMillis();
				synchronized (lastPings) {
					for (int peerIndex = 0; peerIndex < lastPings.size(); ++peerIndex) {
						long lastPing = lastPings.get(peerIndex);

						if (lastPing < now - PING_INTERVAL - PING_ROUND_TRIP_TIME - PRODUCER_SLEEP_TIME)
							throw new RuntimeException("excessive peer ping interval for peer " + peerIndex);

						if (lastPing < now - PING_INTERVAL) {
							lastPings.set(peerIndex, System.currentTimeMillis());
							return new PingTask(peerIndex);
						}
					}
				}

				// No work to do
				return null;
			}
		}

		testEPC(new PingEPC());
	}

}
