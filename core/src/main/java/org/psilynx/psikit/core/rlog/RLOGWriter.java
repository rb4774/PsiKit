// Copyright (c) 2021-2025 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.psilynx.psikit.core.rlog;

import org.psilynx.psikit.core.LogDataReceiver;
import org.psilynx.psikit.core.LogTable;
import org.psilynx.psikit.core.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Sends log data over a socket connection using the RLOG format. */
public class RLOGWriter implements LogDataReceiver {
  private final RLOGEncoder encoder = new RLOGEncoder();
  private final Object encoderLock = new Object();
  private final String filePath;
  private final String folder;
  private FileOutputStream fileOutputStream = null;
  private double lastTimestamp = 0.0;

  public RLOGWriter(String fileName){
    this(
      "/sdcard/FIRST/PsiKit/",
      fileName
    );
  }
  public RLOGWriter(String folder, String fileName){
    if(!folder.endsWith("/")){
      folder = folder + "/";
    }
    if(!fileName.endsWith(".rlog")){
      fileName = fileName + ".rlog";
    }

    this.folder = folder;
    this.filePath = folder + fileName;
  }

  public void start() {
    Logger.logInfo("RLOG writer started");
    try {
      File folderFile = new File(folder);
      //noinspection ResultOfMethodCallIgnored
      folderFile.mkdirs();

      fileOutputStream = new FileOutputStream(filePath, false);
    } catch (IOException e) {
      Logger.logError(
        "error opening log file \"" + filePath + "\": " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n"
        + Arrays.toString(e.getStackTrace())
      );
    }
  }

  @Override
  public void end() {
    try {
      if (fileOutputStream != null) {
        fileOutputStream.flush();
        fileOutputStream.close();
      }
    } catch (IOException e) {
      Logger.logError(
        "error closing log file \""
          + filePath
          + "\"\n"
          + Arrays.toString(e.getStackTrace())
      );
    } finally {
      fileOutputStream = null;
    }
  }

  public void putTable(LogTable table) {
    if(table.getTimestamp() - lastTimestamp > 0.0001) {
      lastTimestamp = table.getTimestamp();
      byte[] data;
      synchronized (encoderLock) {
        encoder.encodeTable(table, true);
        data = encoder.getOutput().array();
      }
      appendData(data);
    }
  }

  private void appendData(byte[] data) {
    try {
      if(fileOutputStream == null){
        Logger.logError(
          "must start RLOGWriter before using append data"
        );
      } else fileOutputStream.write(data);
    }
    catch (IOException e){
      Logger.logError(
        "error opening file \""
        + filePath
        + "\" for writing in the RLOG writer thread\n"
        + Arrays.toString(e.getStackTrace())
      );
    }

  }
}
