package software.xdev.eclipse.store.afs.ibm.access;

import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.iterable.S3Objects;


public interface CosAccessCommunicator
{
	List<String> getExistingFilesWithPrefix();
	
	boolean checkIfFileExists(final String fileName);
	
	void createEmptyFile(final String fileName);
	
	void deleteFile(final String fileName);
	
	public class Default implements CosAccessCommunicator
	{
		
		private final AmazonS3 client;
		private final AccessConfiguration configuration;
		
		public Default(final AccessConfiguration configuration, final AmazonS3 client)
		{
			this.client = client;
			this.configuration = configuration;
		}
		
		@Override
		public List<String> getExistingFilesWithPrefix()
		{
			final ArrayList<String> existingFilesWithPrefix = new ArrayList<>();
			S3Objects
				.withPrefix(this.client, this.configuration.getBucketName(), this.configuration.getAccessFilePrefix())
				.forEach(objectSummary -> existingFilesWithPrefix.add(objectSummary.getKey()));
			return existingFilesWithPrefix;
		}
		
		@Override
		public boolean checkIfFileExists(final String fileName)
		{
			final ArrayList<String> existingFilesWithPrefix = new ArrayList<>();
			return this.client.doesObjectExist(this.configuration.getBucketName(), fileName);
		}
		
		@Override
		public void createEmptyFile(final String fileName)
		{
			this.client.putObject(
				this.configuration.getBucketName(),
				fileName,
				""
			);
		}
		
		@Override
		public void deleteFile(final String fileName)
		{
			this.client.deleteObject(
				this.configuration.getBucketName(),
				fileName
			);
		}
	}
}
