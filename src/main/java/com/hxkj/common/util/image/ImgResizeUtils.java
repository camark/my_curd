package com.hxkj.common.util.image;

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
import java.util.Objects;

/**
 * 图像尺寸缩放
 */
public abstract class ImgResizeUtils {
    private final static Logger LOG = Logger.getLogger(ImgResizeUtils.class);
    private static final MediaTracker tracker = new MediaTracker(new Component() {
        private static final long serialVersionUID = 1234162663955668507L;
    });


    /**
     * 图片缩放
     *
     * @param oldPath 原图像
     * @param newPath 压缩后的图像
     * @param width   图像宽
     * @param format  图片格式 jpg, jpeg, png, gif(非动画)
     */
    public static void resize(String oldPath, String newPath, int width, String format) {
        File originalFile = new File(oldPath);
        File resizedFile = new File(newPath);
        if (format != null && "gif".equals(format.toLowerCase())) {
            resize(originalFile, resizedFile, width, 1);
            return;
        }
        FileInputStream fis = null;
        ByteArrayOutputStream byteStream = null;
        byte[] in;
        try {
            fis = new FileInputStream(originalFile);
            byteStream = new ByteArrayOutputStream();
            int readLength;
            int bufferSize = 1024;
            byte bytes[] = new byte[bufferSize];
            while ((readLength = fis.read(bytes, 0, bufferSize)) != -1) {
                byteStream.write(bytes, 0, readLength);
            }
            in = byteStream.toByteArray();

            Image inputImage = Toolkit.getDefaultToolkit().createImage(in);
            waitForImage(inputImage);
            int imageWidth = inputImage.getWidth(null);
            if (imageWidth < 1) {
                throw new IllegalArgumentException("image width " + imageWidth + " is out of range");
            }
            int imageHeight = inputImage.getHeight(null);
            if (imageHeight < 1) {
                throw new IllegalArgumentException("image height " + imageHeight + " is out of range");
            }

            int height = -1;
            double scaleW = (double) imageWidth / (double) width;
            double scaleY = (double) imageHeight / (double) height;
            if (scaleW >= 0 && scaleY >= 0) {
                if (scaleW <= scaleY) {
                    width = -1;
                }
            }
            Image outputImage = inputImage.getScaledInstance(width, height, java.awt.Image.SCALE_DEFAULT);
            checkImage(outputImage);
            encode(new FileOutputStream(resizedFile), outputImage, format);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            throw new RuntimeException("resize异常");
        } finally {
            try {
                Objects.requireNonNull(fis).close();
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
            try {
                Objects.requireNonNull(byteStream).close();
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
    }

    /**
     * Checks the given image for valid width and height.
     */
    private static void checkImage(Image image) {
        waitForImage(image);
        int imageWidth = image.getWidth(null);
        if (imageWidth < 1) {
            throw new IllegalArgumentException("image width " + imageWidth + " is out of range");
        }
        int imageHeight = image.getHeight(null);
        if (imageHeight < 1) {
            throw new IllegalArgumentException("image height " + imageHeight + " is out of range");
        }

    }

    /**
     * Waits for given image to load. Use before querying image height/width/colors.
     */
    private static void waitForImage(Image image) {
        try {
            tracker.addImage(image, 0);
            tracker.waitForID(0);
            tracker.removeImage(image, 0);
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Encodes the given image at the given quality to the output stream.
     */
    private static void encode(OutputStream outputStream, Image outputImage, String format) throws java.io.IOException {
        int outputWidth = outputImage.getWidth(null);
        if (outputWidth < 1) {
            throw new IllegalArgumentException("output image width " + outputWidth + " is out of range");
        }
        int outputHeight = outputImage.getHeight(null);
        if (outputHeight < 1) {
            throw new IllegalArgumentException("output image height " + outputHeight + " is out of range");
        }
        BufferedImage bi = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D biContext = bi.createGraphics();
        biContext.drawImage(outputImage, 0, 0, null);
        ImageIO.write(bi, format, outputStream);
        outputStream.flush();
        outputStream.close();
    }

    /**
     * 缩放gif图片
     *
     * @param originalFile 原图片
     * @param resizedFile  缩放后的图片
     * @param newWidth     宽度
     * @param quality      缩放比例 (等比例)
     */
    private static void resize(File originalFile, File resizedFile, int newWidth, float quality) {
        if (quality < 0 || quality > 1) {
            throw new IllegalArgumentException("Quality has to be between 0 and 1");
        }
        FileOutputStream out = null;
        try {
            ImageIcon ii = new ImageIcon(originalFile.getCanonicalPath());
            Image i = ii.getImage();
            Image resizedImage;
            int iWidth = i.getWidth(null);
            int iHeight = i.getHeight(null);
            if (iWidth > iHeight) {
                resizedImage = i.getScaledInstance(newWidth, (newWidth * iHeight) / iWidth, Image.SCALE_SMOOTH);
            } else {
                //noinspection SuspiciousNameCombination
                resizedImage = i.getScaledInstance((newWidth * iWidth) / iHeight, newWidth, Image.SCALE_SMOOTH);
            }
            Image temp = new ImageIcon(resizedImage).getImage();
            BufferedImage bufferedImage = new BufferedImage(temp.getWidth(null), temp.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics g = bufferedImage.createGraphics();
            g.setColor(Color.white);
            g.fillRect(0, 0, temp.getWidth(null), temp.getHeight(null));
            g.drawImage(temp, 0, 0, null);
            g.dispose();
            float softenFactor = 0.05f;
            float[] softenArray = {0, softenFactor, 0, softenFactor, 1 - (softenFactor * 4), softenFactor, 0, softenFactor, 0};
            Kernel kernel = new Kernel(3, 3, softenArray);
            ConvolveOp cOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            bufferedImage = cOp.filter(bufferedImage, null);
            out = new FileOutputStream(resizedFile);
            JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
            JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(bufferedImage);
            param.setQuality(quality, true);
            encoder.setJPEGEncodeParam(param);
            encoder.encode(bufferedImage);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            try {
                Objects.requireNonNull(out).close();
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

}
