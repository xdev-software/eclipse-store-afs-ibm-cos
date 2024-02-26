package software.xdev.eclipse.store.afs.ibm.access;

public class AccessToken implements AutoCloseable
{
	private final String fileName;
	private final SingleAccessManager manager;
	
	public AccessToken(final String fileName, final SingleAccessManager manager)
	{
		this.fileName = fileName;
		this.manager = manager;
	}
	
	public String getFileName()
	{
		return this.fileName;
	}
	
	@Override
	public void close()
	{
		this.manager.releaseToken(this);
	}
}
