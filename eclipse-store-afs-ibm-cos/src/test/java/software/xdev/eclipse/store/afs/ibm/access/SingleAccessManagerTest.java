/*
 * Copyright © 2023 XDEV Software (https://xdev.software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.xdev.eclipse.store.afs.ibm.access;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.eclipse.store.storage.types.StorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


class SingleAccessManagerTest
{
	public static final int KEEP_ALIVE_INTERVAL_FOR_TOKEN = 100;
	private AccessConfiguration configuration;
	private CosAccessCommunicatorLocal communicator;
	
	@BeforeEach
	void init()
	{
		this.configuration = new AccessConfiguration(
			"PREFIX-",
			"TEST-BUCKET",
			10,
			10,
			KEEP_ALIVE_INTERVAL_FOR_TOKEN);
		this.communicator = new CosAccessCommunicatorLocal(this.configuration);
	}
	
	public SingleAccessManager createManager()
	{
		return new SingleAccessManager(this.configuration, this.communicator);
	}
	
	@Test
	void isSingleAccessAvailable_true()
	{
		try(final SingleAccessManager manager1 = this.createManager())
		{
			Assertions.assertTrue(manager1.isSingleAccessAvailable());
		}
	}
	
	@Test
	void isSingleAccessAvailable_false()
	{
		try(final SingleAccessManager manager1 = this.createManager())
		{
			manager1.waitForAndReserveSingleAccess();
			try(final SingleAccessManager manager2 = this.createManager())
			{
				Assertions.assertFalse(manager2.isSingleAccessAvailable());
			}
		}
	}
	
	@Test
	void waitForAndReserveSingleAccess_NoWaitingNeeded()
	{
		try(final SingleAccessManager manager1 = this.createManager())
		{
			Assertions.assertTimeout(Duration.ofMillis(100), manager1::waitForAndReserveSingleAccess);
		}
	}
	
	@Test
	void waitForAndReserveSingleAccess_WaitingNeeded()
	{
		try(final SingleAccessManager manager1 = this.createManager())
		{
			manager1.waitForAndReserveSingleAccess();
			try(final SingleAccessManager manager2 = this.createManager())
			{
				final long delay = 100L;
				final Timer timer = new Timer();
				timer.schedule(
					new TimerTask()
					{
						@Override
						public void run()
						{
							manager1.close();
						}
					},
					delay
				);
				Assertions.assertFalse(manager2.isSingleAccessAvailable());
				Assertions.assertTimeout(Duration.ofMillis(delay + 1000), manager2::waitForAndReserveSingleAccess);
				Assertions.assertTrue(manager2.isSingleAccessAvailable());
			}
		}
	}
	
	/**
	 * Starts a lot of threads which all want the same, single access. If they all finish without deadlock and no
	 * Access-files are left, the test is successful.
	 */
	@Test
	void waitForAndReserveSingleAccess_WaitingNeeded_ManyManagers()
	{
		final ExecutorService executor = Executors.newFixedThreadPool(10);
		
		IntStream
			.rangeClosed(1, KEEP_ALIVE_INTERVAL_FOR_TOKEN)
			.mapToObj(
				i ->
					executor.submit(() -> {
						final SingleAccessManager manager = this.createManager();
						manager.registerTerminateAccessListener(manager::close);
						manager.waitForAndReserveSingleAccess();
					}))
			.forEach(
				future ->
					Assertions.assertTimeout(
						Duration.ofSeconds(5),
						() -> future.get())
			);
		
		// Only the last manager is allowed to still have the AccessToken file
		Assertions.assertEquals(1, this.communicator.getExistingFilesWithPrefix().size());
	}
	
	@Test
	void waitForAndReserveSingleAccess_IgnoringOldFiles()
	{
		this.communicator.createEmptyFile(this.configuration.getAccessFilePrefix() + "DUMMY");
		try(final SingleAccessManager manager1 = this.createManager())
		{
			Assertions.assertTimeout(
				Duration.ofMillis(KEEP_ALIVE_INTERVAL_FOR_TOKEN * 3),
				manager1::waitForAndReserveSingleAccess);
		}
	}
	
	@Test
	void shutdownStorageWhenAccessShouldTerminate()
	{
		final StorageManager storageManager = Mockito.mock(StorageManager.class);
		try(final SingleAccessManager manager1 = this.createManager())
		{
			manager1.waitForAndReserveSingleAccess();
			manager1.shutdownStorageWhenAccessShouldTerminate(storageManager);
			try(final SingleAccessManager manager2 = this.createManager())
			{
				manager2.waitForAndReserveSingleAccess();
				Mockito.verify(storageManager).shutdown();
			}
		}
	}
	
	@Test
	void registerTerminateAccessListener()
	{
		try(final SingleAccessManager manager1 = this.createManager())
		{
			manager1.waitForAndReserveSingleAccess();
			manager1.registerTerminateAccessListener(manager1::close);
			try(final SingleAccessManager manager2 = this.createManager())
			{
				Assertions.assertTimeout(Duration.ofMillis(100), manager2::waitForAndReserveSingleAccess);
			}
		}
	}
}
