package software.xdev.eclipse.store.afs.ibm.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.serializer.util.logging.Logging;
import org.slf4j.Logger;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;


public class SingleAccessManager implements AutoCloseable
{
	private static final Logger LOGGER = Logging.getLogger(SingleAccessManager.class);
	
	private final List<TerminateAccessListener> listeners = new ArrayList<>();
	private final AccessConfiguration configuration;
	private final CosAccessCommunicator communicator;
	private Timer terminateAccessCheckTimer;
	
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
		}
		return this.token;
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
		LOGGER.info("Released access token {}", tokenFileName);
	}
	
	public AccessToken waitForAndReserveSingleAccess()
	{
		LOGGER.info("Waiting for single access...");
		final AccessToken newToken = this.createToken();
		try
		{
			while(this.checkIfOtherTokensExist() && !Thread.interrupted())
			{
				Thread.sleep(this.configuration.getCheckIntervalForSingleAccess());
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
	
	private boolean checkIfOtherTokensExist()
	{
		return this.communicator
			.getExistingFilesWithPrefix().stream()
			.anyMatch(fileName -> !fileName.equals(this.token.getFileName()));
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
					if(SingleAccessManager.this.checkIfOtherTokensExist())
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
