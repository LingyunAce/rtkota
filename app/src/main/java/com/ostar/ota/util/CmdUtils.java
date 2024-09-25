package com.ostar.ota.util;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;


public class CmdUtils {


    public static String exec2(String command) {
        StringBuilder strBuilder = new StringBuilder();
        BufferedReader reader = null;
        BufferedReader errorReader = null;
        try {
            // 执行命令
            Process process = Runtime.getRuntime().exec(command);
            // 获取命令的输出流（命令的结果）
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;

            // 读取输出流
            while ((line = reader.readLine()) != null) {
                strBuilder.append(line);
            }
            while ((line = errorReader.readLine()) != null) {
                strBuilder.append(line);
            }

            // 等待命令执行完成
            process.waitFor();

            // 获取退出值
            int exitValue = process.exitValue();
            System.out.println("Exit value: " + exitValue);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return strBuilder.toString();
    }

    public static String execSrc(String cmd) {
        String result = "";
        DataOutputStream dos = null;
        DataInputStream dis = null;
        DataInputStream der = null;
        try {
            Process p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            dis = new DataInputStream(p.getInputStream());
            der = new DataInputStream(p.getErrorStream());
            dos.writeBytes(cmd + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            String line = null;
            while ((line = dis.readLine()) != null) {
                result += line;
            }
            while ((line = der.readLine()) != null) {
                result += line;
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (der != null) {
                try {
                    der.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static String exec(String cmd) {
      return exec2(cmd);
    }

    public static String execPSAndGrep(String target) {
        BufferedReader reader = null;
        BufferedReader errorReader = null;
        try {
            String command = "ps -A";
            // 执行命令
            Process process = Runtime.getRuntime().exec(command);

            // 获取命令的输出流（命令的结果）
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;

            // 读取输出流
            while ((line = reader.readLine()) != null) {
                if (line.contains(target)) {
                    return line;
                }
            }
            while ((line = errorReader.readLine()) != null) {
            }

            // 等待命令执行完成
            process.waitFor();

            // 获取退出值
            int exitValue = process.exitValue();
            System.out.println("Exit value: " + exitValue);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return "";
    }

    public static String execPGrepShTask(String target) {
        BufferedReader reader = null;
        BufferedReader errorReader = null;
        try {
            String command = "pgrep -f " + target;
            // 执行命令
            Process process = Runtime.getRuntime().exec(command);

            // 获取命令的输出流（命令的结果）
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;

            // 读取输出流
            while ((line = reader.readLine()) != null) {
                return line;
            }
            while ((line = errorReader.readLine()) != null) {
            }

            // 等待命令执行完成
            process.waitFor();

            // 获取退出值
            int exitValue = process.exitValue();
            System.out.println("Exit value: " + exitValue);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return "";
    }

    public static void execCmd(String cmd) {
//        LOGD("CmdUtils", "execCmd " + cmd);
        OutputStream outputStream = null;
        DataOutputStream dataOutputStream = null;
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            outputStream = p.getOutputStream();
            dataOutputStream = new DataOutputStream(outputStream);
            dataOutputStream.writeBytes(cmd);
            dataOutputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != dataOutputStream) {
                try {
                    dataOutputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (null != outputStream) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean execCmdWithResult(String cmd, String[] results) {
        InputStreamReader reader = null;
        BufferedReader bufferedReader = null;
        String lineText = null;
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            reader = new InputStreamReader(process.getInputStream());
            bufferedReader = new BufferedReader(reader);
            while ((lineText = bufferedReader.readLine()) != null) {
                for (String result : results) {
                    if (lineText.contains(result)) {
//                        LOGD("CmdUtils", "execCmd=" + cmd + ", have result=" + lineText);
                        return true;
                    }
                }
//                LOGD("CmdUtils", "lineText=" + lineText);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != bufferedReader) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
//        LOGE("CmdUtils", "execCmd=" + cmd + ", but not result=" + results);
        return false;
    }

    public static boolean getNpuCodeStatus() {
        return execCmdWithResult("lsusb",
                new String[]{"2207:1808", "2207:1005"});
    }
}
