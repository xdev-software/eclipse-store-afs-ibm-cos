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

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.store.storage.types.StorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


class SingleAccessManagerTest
{
	private AccessConfiguration configuration;
	private CosAccessCommunicatorLocal communicator;
	
	@BeforeEach
	void init()
	{
		this.configuration = new AccessConfiguration(
			"SOME-PREFIX",
		"TEST-BUCKET",
		10,
		10,
		1000);
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
			Assertions.assertNotNull(manager1.waitForAndReserveSingleAccess());
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
					100L
				);
				Assertions.assertFalse(manager2.isSingleAccessAvailable());
				Assertions.assertNotNull(manager2.waitForAndReserveSingleAccess());
				Assertions.assertTrue(manager2.isSingleAccessAvailable());
			}
		}
	}
	
	@Test
	void waitForAndReserveSingleAccess_WaitingNeeded_3Manager() throws InterruptedException
	{
		final ExecutorService executor = Executors.newFixedThreadPool(10);
		
		final List<? extends Future<?>> waitingManagers = IntStream.rangeClosed(1, 100).mapToObj(
			i -> this.createManager()
		).map(
			manager ->
				executor.submit(() -> {
					manager.registerTerminateAccessListener(() ->
						manager.close());
					manager.waitForAndReserveSingleAccess();
				})
		).collect(Collectors.toList());
		
		waitingManagers.forEach(future -> {
			try
			{
				future.get();
			}
			catch(final Exception e)
			{
				throw new RuntimeException(e);
			}
		});
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
		final TerminateAccessListener accessListener = Mockito.mock(TerminateAccessListener.class);
		try(final SingleAccessManager manager1 = this.createManager())
		{
			manager1.waitForAndReserveSingleAccess();
			manager1.registerTerminateAccessListener(accessListener);
			try(final SingleAccessManager manager2 = this.createManager())
			{
				manager2.waitForAndReserveSingleAccess();
				Mockito.verify(accessListener).accessTerminationRequested();
			}
		}
	}
}
