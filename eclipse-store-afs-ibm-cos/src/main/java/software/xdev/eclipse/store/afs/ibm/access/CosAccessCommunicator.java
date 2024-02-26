package software.xdev.eclipse.store.afs.ibm.access;

import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.iterable.S3Objects;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


public interface CosAccessCommunicator
{
	List<S3ObjectSummary> getExistingFilesWithPrefix();
	
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
		public List<S3ObjectSummary> getExistingFilesWithPrefix()
		{
			final ArrayList<S3ObjectSummary> existingFilesWithPrefix = new ArrayList<>();
			S3Objects
				.withPrefix(this.client, this.configuration.getBucketName(), this.configuration.getAccessFilePrefix())
				.forEach(existingFilesWithPrefix::add);
			return existingFilesWithPrefix;
		}
		
		@Override
		public boolean checkIfFileExists(final String fileName)
		{
			try
			{
				return this.client.doesObjectExist(this.configuration.getBucketName(), fileName);
			}
			catch(final AmazonS3Exception e)
			{
				// This is a "feature": https://github.com/aws/aws-sdk-java/issues/974#issuecomment-272634444
				if(e.getStatusCode() == 403)
				{
					return false;
				}
				throw e;
			}
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
