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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


public class CosAccessCommunicatorLocal implements CosAccessCommunicator
{
	private final AccessConfiguration configuration;
	private final HashMap<String, S3ObjectSummary> existingFiles = new HashMap<>();
	
	public CosAccessCommunicatorLocal(final AccessConfiguration configuration)
	{
		this.configuration = configuration;
	}
	
	@Override
	public synchronized List<S3ObjectSummary> getExistingFilesWithPrefix()
	{
		return this.existingFiles
			.values()
			.stream()
			.filter(s3Object -> s3Object.getKey().startsWith(this.configuration.getAccessFilePrefix()))
			.collect(Collectors.toList());
	}
	
	@Override
	public synchronized boolean checkIfFileExists(final String fileName)
	{
		return this.existingFiles.containsKey(fileName);
	}
	
	@Override
	public synchronized void createEmptyFile(final String fileName)
	{
		if(this.existingFiles.containsKey(fileName))
		{
			this.existingFiles.get(fileName).setLastModified(new Date());
			return;
		}
		final S3ObjectSummary s3ObjectSummary = new S3ObjectSummary();
		s3ObjectSummary.setKey(fileName);
		s3ObjectSummary.setLastModified(new Date());
		this.existingFiles.put(fileName, s3ObjectSummary);
	}
	
	@Override
	public synchronized void deleteFile(final String fileName)
	{
		this.existingFiles.remove(fileName);
	}
}
