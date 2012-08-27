package org.sakaiproject.nakamura.files.jdo;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;
import org.sakaiproject.nakamura.api.files.File;
import org.sakaiproject.nakamura.api.files.FileParams;
import org.sakaiproject.nakamura.api.files.FileService;
import org.sakaiproject.nakamura.api.files.StorageException;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import java.io.IOException;

@Component
@Service
public class FileServiceJdo implements FileService {

  @Reference
  PersistenceManagerFactory persistenceManagerFactory;

  @Reference
  ClusterTrackingService clusterTrackingService;

  @Override
  public File createFile(FileParams params) throws StorageException, IOException {
    PersistenceManager persistenceManager = persistenceManagerFactory.getPersistenceManager();
    String poolID = clusterTrackingService.getClusterUniqueId();
    File file = new File(params.getCreator(), params.getFilename(), params.getContentType(), poolID, params.getProperties());
    Transaction transaction = persistenceManager.currentTransaction();
    transaction.begin();
    persistenceManager.makePersistent(file);
    transaction.commit();
    persistenceManager.close();
    return file;
  }

  @Override
  public File createAlternativeStream(FileParams params) throws StorageException, IOException {
    return createFile(params);
  }

  @Override
  public File updateFile(FileParams params) throws StorageException, IOException {
    String poolID = params.getPoolID();
    PersistenceManager persistenceManager = persistenceManagerFactory.getPersistenceManager();
    persistenceManager.currentTransaction().begin();
    File file = persistenceManager.getObjectById(File.class, poolID);
    file.updateFromParams(params);
    persistenceManager.currentTransaction().commit();
    persistenceManager.close();
    return file;
  }
}