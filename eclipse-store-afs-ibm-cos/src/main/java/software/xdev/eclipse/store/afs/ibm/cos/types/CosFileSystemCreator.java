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
package software.xdev.eclipse.store.afs.ibm.cos.types;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;

import software.xdev.eclipse.store.afs.ibm.CosClientCreator;


public class CosFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	public CosFileSystemCreator()
	{
		super(AFileSystem.class, "ibm.cos");
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final AmazonS3 client = CosClientCreator.createClient(configuration);
		final boolean cache = configuration.optBoolean("cache").orElse(true);
		final CosConnector connector = cache
			? CosConnector.Caching(client)
			: CosConnector.New(client);
		return BlobStoreFileSystem.New(connector);
	}
}
