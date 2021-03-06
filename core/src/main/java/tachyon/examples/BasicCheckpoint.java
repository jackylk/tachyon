package tachyon.examples;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.Version;
import tachyon.client.TachyonByteBuffer;
import tachyon.client.TachyonFS;
import tachyon.client.TachyonFile;
import tachyon.client.WriteType;
import tachyon.master.DependencyType;
import tachyon.util.CommonUtils;

/**
 * An example to show to how use Tachyon's API
 */
public class BasicCheckpoint implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);

  private final TachyonURI mLocation;
  private final String mFileFolder;
  private final int mNumFiles;

  public BasicCheckpoint(TachyonURI tachyonURI, String fileFolder, int numFiles) {
    mLocation = tachyonURI;
    mFileFolder = fileFolder;
    mNumFiles = numFiles;
  }

  @Override
  public Boolean call() throws Exception {
    TachyonFS tachyonClient = TachyonFS.get(mLocation);
    createDependency(tachyonClient);
    writeFile(tachyonClient);
    return readFile(tachyonClient);
  }

  private void createDependency(TachyonFS tachyonClient) throws IOException {
    long startTimeMs = CommonUtils.getCurrentMs();
    List<String> children = new ArrayList<String>();
    for (int k = 0; k < mNumFiles; k ++) {
      children.add(mFileFolder + "/part-" + k);
    }
    List<ByteBuffer> data = new ArrayList<ByteBuffer>();
    data.add(ByteBuffer.allocate(10));
    int depId =
        tachyonClient.createDependency(new ArrayList<String>(), children, "fake command", data,
            "BasicCheckpoint Dependency", "Tachyon Examples", "0.3",
            DependencyType.Narrow.getValue(), 512 * Constants.MB);

    CommonUtils.printTimeTakenMs(startTimeMs, LOG, "createDependency with depId " + depId);
  }

  private boolean readFile(TachyonFS tachyonClient) throws IOException {
    boolean pass = true;
    for (int i = 0; i < mNumFiles; i ++) {
      TachyonURI filePath = new TachyonURI(mFileFolder + "/part-" + i);
      LOG.debug("Reading data from {}", filePath);
      TachyonFile file = tachyonClient.getFile(filePath);
      TachyonByteBuffer buf = file.readByteBuffer(0);
      if (buf == null) {
        file.recache();
        buf = file.readByteBuffer(0);
      }
      buf.DATA.order(ByteOrder.nativeOrder());
      for (int k = 0; k < mNumFiles; k ++) {
        pass = pass && (buf.DATA.getInt() == k);
      }
      buf.close();
    }
    return pass;
  }

  private void writeFile(TachyonFS tachyonClient) throws IOException {
    for (int i = 0; i < mNumFiles; i ++) {
      TachyonURI filePath = new TachyonURI(mFileFolder + "/part-" + i);
      TachyonFile file = tachyonClient.getFile(filePath);
      OutputStream os = file.getOutStream(WriteType.ASYNC_THROUGH);

      ByteBuffer buf = ByteBuffer.allocate(80);
      buf.order(ByteOrder.nativeOrder());
      for (int k = 0; k < mNumFiles; k ++) {
        buf.putInt(k);
      }
      buf.flip();
      LOG.debug("Writing data to {}", filePath);
      os.write(buf.array());
      os.close();
    }
  }

  public static void main(String[] args) throws IOException, TException {
    if (args.length != 3) {
      System.out.println("java -cp target/tachyon-" + Version.VERSION
          + "-jar-with-dependencies.jar "
          + "tachyon.examples.BasicCheckpoint <TachyonMasterAddress> <FileFolder> <Files>");
      System.exit(-1);
    }

    Utils.runExample(new BasicCheckpoint(new TachyonURI(args[0]), args[1], Integer
        .parseInt(args[2])));
  }
}
