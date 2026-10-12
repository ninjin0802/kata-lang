import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Prints "r g b a" for one pixel of a PNG. Usage: java scripts/Pixel.java <png> <x> <y> */
public class Pixel {
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        int argb = image.getRGB(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        System.out.println(((argb >> 16) & 0xff) + " " + ((argb >> 8) & 0xff) + " " + (argb & 0xff) + " "
                + ((argb >>> 24) & 0xff));
    }
}
