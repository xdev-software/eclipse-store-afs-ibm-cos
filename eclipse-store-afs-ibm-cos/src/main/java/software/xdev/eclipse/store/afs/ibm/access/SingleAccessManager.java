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
import org.slf4j.Logger;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


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
	
	public void releaseToken()
	{
		if(this.token != null)
		{
			this.releaseToken(this.token);
		}
	}
	
	public void releaseToken(final AccessToken token)
	{
		final String tokenFileName = token.getFileName();
		this.communicator.deleteFile(tokenFileName);
		this.token = null;
		if(this.keepAliveTokenTimer != null)
		{
			this.keepAliveTokenTimer.cancel();
			this.keepAliveTokenTimer = null;
		}
		LOGGER.info("Released access token {}", tokenFileName);
	}
	
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
				while(this.checkIfOtherTokensExistAndDeleteInvalidTokens() && !Thread.interrupted());
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
		
		return newToken;
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
	
	@Override
	public void close()
	{
		if(this.terminateAccessCheckTimer != null)
		{
			this.terminateAccessCheckTimer.cancel();
		}
		this.releaseToken();
	}
}
