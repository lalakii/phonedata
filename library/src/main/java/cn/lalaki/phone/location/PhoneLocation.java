package cn.lalaki.phone.location;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/***
 * <p>
 * 手机号归属地查询类实现
 *
 * @author lalaki i@lalaki.cn
 */

public class PhoneLocation {
    private static final ThreadLocal<byte[]> LOCAL_BYTES = ThreadLocal.withInitial(() -> new byte[100]);
    private static final AtomicReference<MappedByteBuffer> ATOMIC_BUFFER = new AtomicReference<>();
    private static final String[] CARD_TYPES = {"中国移动", "中国联通", "中国电信", "中国广电", "中国电信虚拟运营商", "中国联通虚拟运营商", "中国移动虚拟运营商", "中国广电虚拟运营商"};
    private static final String UNKNOWN_CARRIER = "未知运营商";
    private static File dataFile;
    private static File TEMP_DIR;

    public record PhoneInfo(String province, String city, String zipCode, String areaCode,
                            String carrier, String version) {
        public static PhoneInfo fromArray(String[] arr) {
            return new PhoneInfo(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]);
        }
    }

    /***
     * 从本地文件加载数据库时调用, 一般无需调用
     * @param dat phone.dat 文件
     */
    @SuppressWarnings("unused")
    public static void loadDAT(File dat) {
        if (dat != null && dat.isFile() && dat.canRead()) {
            dataFile = dat;
        } else {
            throw new IllegalArgumentException("Invalid data file: " + dat);
        }
    }

    /***
     * 清理数据库缓存, 需要重新加载数据文件 或 内存紧张时调用
     */
    @SuppressWarnings("unused")
    public static void clearCache() {
        ATOMIC_BUFFER.set(null);
    }

    /**
     * @param tempDir 自定义数据库缓存文件夹，必须是一个存在并且可以读写的文件夹
     */
    @SuppressWarnings("unused")
    public static void setTempDirectory(File tempDir) {
        TEMP_DIR = tempDir;
    }

    public static PhoneInfo queryPhoneInfo(String numberText) throws IOException {
        int numberPrefix;
        String trimNumberText = numberText.trim();
        if (trimNumberText.length() > 6) {
            numberPrefix = Integer.parseInt(trimNumberText.substring(0, 7));
            MappedByteBuffer buffer = ATOMIC_BUFFER.get();
            if (buffer == null) {
                synchronized (ATOMIC_BUFFER) {
                    buffer = ATOMIC_BUFFER.get();
                    if (buffer == null) {
                        buffer = mapBuffer();
                    }
                }
            }
            if (buffer == null) {
                throw new IOException("Failed to load data file: phone.dat!");
            }
            String info = query(buffer, buffer.capacity(), numberPrefix);
            if (info != null) {
                String[] infoArray = info.split(Pattern.quote("|"));
                if (infoArray.length == 6) {
                    return PhoneInfo.fromArray(infoArray);
                }
            }
        }
        return null;
    }

    private static String query(MappedByteBuffer buffer, long fileSize, int numberPrefix) {
        int version = buffer.getInt(0);
        int firstIndexOffset = buffer.getInt(4);
        int totalIndices = (int) ((fileSize - firstIndexOffset) / 9);
        int left = 0;
        int right = totalIndices - 1;
        while (left <= right) {
            int mid = left + ((right - left) >>> 1);
            int currentIdxPos = firstIndexOffset + mid * 9;
            int currentPhone = buffer.getInt(currentIdxPos);
            if (currentPhone == numberPrefix) {
                int dataOffset = buffer.getInt(currentIdxPos + 4);
                int cardType = (buffer.get(currentIdxPos + 8) & 0xFF) - 1;
                byte[] bytes = LOCAL_BYTES.get();
                int len = 0;
                for (; len < 100 && (dataOffset + len) < fileSize; len++) {
                    byte b = buffer.get(dataOffset + len);
                    if (b == 0) {
                        break;
                    }
                    bytes[len] = b;
                }
                return String.format("%s|%s|%s", new String(bytes, 0, len, StandardCharsets.UTF_8), getCardTypeValue(cardType), int32ToAscii(version));
            } else if (currentPhone < numberPrefix) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return null;
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "TryFinallyCanBeTryWithResources"})
    private static File extractResourceToFile(InputStream ris) {
        byte[] version = new byte[4];
        try {
            ris.read(version);
        } catch (IOException ignored) {
        }
        File tempDir = TEMP_DIR;
        if (tempDir == null || !tempDir.isDirectory()) {
            tempDir = new File(System.getProperty("java.io.tmpdir"), "phone_data_ver_" + bytesToAscii(version));
        }
        boolean existsDir = tempDir.isDirectory();
        if (!existsDir) {
            try {
                tempDir.delete();
            } catch (Throwable ignored) {
            }
            try {
                existsDir = tempDir.mkdirs();
            } catch (Throwable ignored) {
            }
        }
        if (existsDir) {
            File data = new File(tempDir, "phone.dat");
            boolean existsFile = data.isFile();
            if (!existsFile) {
                try {
                    data.delete();
                } catch (Throwable ignored) {
                }
                byte[] buffer = new byte[2048];
                int bytesRead;
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(data);
                    fos.write(version);
                    while ((bytesRead = ris.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    existsFile = true;
                } catch (Throwable ignored) {

                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
            if (existsFile) {
                return data;
            }
        }
        return null;
    }

    private static MappedByteBuffer mapBuffer() {
        RandomAccessFile raf1 = null;
        FileChannel channel = null;
        InputStream fis = null;
        try {
            File data = dataFile;
            if (data == null) {
                fis = PhoneLocation.class.getResourceAsStream("/assets/phone.dat");
                if (fis != null) {
                    data = extractResourceToFile(fis);
                }
            }
            if (data != null) {
                raf1 = new RandomAccessFile(data, "r");
                channel = raf1.getChannel();
                long fileSize = channel.size();
                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                ATOMIC_BUFFER.set(buffer);
                return buffer;
            }
        } catch (IOException ignored) {
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {

                }
            }
            if (raf1 != null) {
                try {
                    raf1.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private static String getCardTypeValue(int cardType) {
        String type;
        if (cardType > -1 && cardType < CARD_TYPES.length) {
            type = CARD_TYPES[cardType];
        } else {
            type = UNKNOWN_CARRIER;
        }
        return type;
    }

    private static String bytesToAscii(byte[] bytes) {
        return byteBufferToAscii(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    }

    private static String int32ToAscii(int value) {
        return byteBufferToAscii(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value));
    }

    private static String byteBufferToAscii(ByteBuffer buffer) {
        return new String(buffer.array());
    }
}
