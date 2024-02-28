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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.types.StorageManager;
import org.slf4j.Logger;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


/**
 * Handles access to a single IBM Cos resource and ensures that only one process is using the resource.
 * <p>
 *     Uses {@link Timer} and Token-Files in IBM COS to communicate. That's why it needs to
 *     be closed.
 * </p>
 */
public class SingleAccessManager implements AutoCloseable
{
	private static final Logger LOGGER = Logging.getLogger(SingleAccessManager.class);
	
	private final List<TerminateAccessListener> listeners = new ArrayList<>();
	private final AccessConfiguration configuration;
	private final CosAccessCommunicator communicator;
	private Timer terminateAccessCheckTimer;
	private Timer keepAliveTokenTimer;
	
	private AccessToken token;
	
	public SingleAccessManager(final AccessConfiguration configuration, final AmazonS3 client)
	{
		this(
			configuration,
			new CosAccessCommunicator.Default(configuration, client)
		);
	}
	
	public SingleAccessManager(final AccessConfiguration configuration, final CosAccessCommunicator communicator)
	{
		this.configuration = configuration;
		this.communicator = communicator;
	}
	
	private AccessToken createToken()
	{
		if(this.token == null)
		{
			AccessToken tempToken;
			final Random random = new Random();
			do
			{
				final Integer randomNumber = random.nextInt(1_000_000);
				final String randomNumberFormatted = String.format("%06d", randomNumber);
				tempToken = new AccessToken(this.configuration.getAccessFilePrefix() + randomNumberFormatted, this);
			}
			while(this.communicator.checkIfFileExists(tempToken.getFileName()));
			this.token = tempToken;
			this.communicator.createEmptyFile(this.token.getFileName());
			LOGGER.info("Created and written token {} to cloud.", this.token.getFileName());
			this.startTokenKeepAlive(this.token);
		}
		return this.token;
	}
	
	private void startTokenKeepAlive(final AccessToken token)
	{
		if(this.keepAliveTokenTimer != null)
		{
			throw new RuntimeException("May not start multiple token keep alive timers!");
		}
		final TimerTask keepAliveTokenTask = new TimerTask()
		{
			@Override
			public void run()
			{
				SingleAccessManager.this.communicator.createEmptyFile(token.getFileName());
				LOGGER.debug("Touched keep alive token.");
			}
		};
		this.keepAliveTokenTimer = new Timer("KeepAliveTokenForEclipseStoreIbmCos");
		this.keepAliveTokenTimer.scheduleAtFixedRate(
			keepAliveTokenTask,
			this.configuration.getKeepAliveIntervalForToken(),
			this.configuration.getKeepAliveIntervalForToken());
	}
	
	/**
	 * Releases the token from IBM COS which basically removes the token file from the bucket.
	 *
	 * @param token to release
	 */
	void releaseToken(final AccessToken token)
	{
		if(this.token != null)
		{
			this.closeKeepAliveTokenTimer();
			final String tokenFileName = token.getFileName();
			this.communicator.deleteFile(tokenFileName);
			this.token = null;
			LOGGER.info("Released access token {}", tokenFileName);
		}
	}
	
	/**
	 * Checks if other tokens exist and if this manager holds the single access.
	 *
	 * @return if this manager holds the single access
	 */
	public boolean isSingleAccessAvailable()
	{
		return !this.checkIfOtherTokensExistAndDeleteInvalidTokens();
	}
	
	/**
	 * Blocks the calling thread until this manager holds single access.
	 * <p>
	 * Periodically checks if there are other tokens and waits until there is no other token.
	 * </p>
	 *
	 * @return the access token that reserves the single access. If the token is released, single access is released as
	 * well.
	 */
	public AccessToken waitForAndReserveSingleAccess()
	{
		final AccessToken newToken = this.createToken();
		try
		{
			if(this.checkIfOtherTokensExistAndDeleteInvalidTokens())
			{
				LOGGER.info("Active access from different client found. Waiting for single access...");
				do
				{
					Thread.sleep(this.configuration.getCheckIntervalForSingleAccess());
				}
				while(
					this.checkIfOtherTokensExistAndDeleteInvalidTokens()
						&& !Thread.interrupted()
						&& this.token != null
				);
			}
			LOGGER.info("Received and reserved single access.");
		}
		catch(final InterruptedException e)
		{
			LOGGER.info("{} interrupted while waiting for single access.", SingleAccessManager.class.getSimpleName());
			newToken.close();
			Thread.currentThread().interrupt();
			return null;
		}
		
		return this.token;
	}
	
	private boolean checkIfOtherTokensExistAndDeleteInvalidTokens()
	{
		final List<S3ObjectSummary> existingFilesWithPrefix = this.communicator
			.getExistingFilesWithPrefix()
			.stream()
			.filter(this::isNotThisTokenFile)
			.collect(Collectors.toList());
		
		final List<S3ObjectSummary> oldTokens =
			existingFilesWithPrefix
				.stream()
				.filter(this::isOldTokenFile)
				.collect(Collectors.toList());
		
		if(!oldTokens.isEmpty())
		{
			oldTokens.forEach(oldTokenS3ObjectSummary ->
			{
				this.communicator.deleteFile(oldTokenS3ObjectSummary.getKey());
				LOGGER.info(String.format("Deleted old token %s", oldTokenS3ObjectSummary.getKey()));
			});
			
			existingFilesWithPrefix.removeAll(oldTokens);
		}
		
		return !existingFilesWithPrefix.isEmpty();
	}
	
	private boolean isNotThisTokenFile(final S3ObjectSummary s3ObjectSummary)
	{
		if(this.token == null)
		{
			return true;
		}
		return !s3ObjectSummary.getKey().equals(this.token.getFileName());
	}
	
	private boolean isOldTokenFile(final S3ObjectSummary s3ObjectSummary)
	{
		final Calendar deadlineForOldToken = Calendar.getInstance();
		deadlineForOldToken.add(
			Calendar.MILLISECOND,
			(int)(this.configuration.getKeepAliveIntervalForToken() * -2 - 1));
		final Date deadlineForOldTokenDate = deadlineForOldToken.getTime();
		return s3ObjectSummary.getLastModified().before(deadlineForOldTokenDate);
	}
	
	/**
	 * Registers to listen on a terminate access request (which is basically another token getting created). If this
	 * request is received the given storage is shutdown (which is a safe action while stores are beeing executed) and
	 * the token is released.
	 *
	 * @param storage to shutdown
	 */
	public void shutdownStorageWhenAccessShouldTerminate(final StorageManager storage)
	{
		this.registerTerminateAccessListener(
			() ->
			{
				// Shutdown command is safe to execute even with stores currently running.
				storage.shutdown();
				this.releaseToken(this.token);
			}
		);
	}
	
	/**
	 * Registers to listen on a terminate access request (which is basically another token getting created). If this
	 * request is received the {@link TerminateAccessListener#accessTerminationRequested()} is executed.
	 * <p>
	 * It periodically checks if other tokens exist.
	 * </p>
	 */
	public void registerTerminateAccessListener(final TerminateAccessListener listener)
	{
		this.listeners.add(listener);
		LOGGER.info("Registered new terminate access listener.");
		
		if(this.terminateAccessCheckTimer == null)
		{
			final TimerTask terminateAccessCheckTask = new TimerTask()
			{
				@Override
				public void run()
				{
					LOGGER.debug("Checking if other tokens exist...");
					if(SingleAccessManager.this.checkIfOtherTokensExistAndDeleteInvalidTokens())
					{
						LOGGER.debug("Other tokens do exist. Notifying all listeners.");
						SingleAccessManager.this.listeners.forEach(TerminateAccessListener::accessTerminationRequested);
						SingleAccessManager.this.closeTerminateAccessCheckTimer();
					}
				}
			};
			this.terminateAccessCheckTimer = new Timer("TerminateAccessForEclipseStoreIbmCosChecker");
			this.terminateAccessCheckTimer.scheduleAtFixedRate(
				terminateAccessCheckTask,
				this.configuration.getCheckIntervalForTerminateAccess(),
				this.configuration.getCheckIntervalForTerminateAccess());
			
			LOGGER.info("Registered new terminate access listener.");
		}
	}
	
	private void closeTerminateAccessCheckTimer()
	{
		if(this.terminateAccessCheckTimer != null)
		{
			this.terminateAccessCheckTimer.cancel();
			this.terminateAccessCheckTimer = null;
		}
	}
	
	private void closeKeepAliveTokenTimer()
	{
		if(this.keepAliveTokenTimer != null)
		{
			this.keepAliveTokenTimer.cancel();
			this.keepAliveTokenTimer = null;
		}
	}
	
	@Override
	public void close()
	{
		this.closeTerminateAccessCheckTimer();
		this.closeKeepAliveTokenTimer();
		this.releaseToken(this.token);
	}
}
