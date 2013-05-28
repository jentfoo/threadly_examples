package org.threadly.examples.fractals;

import java.awt.Canvas;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.MemoryImageSource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.threadly.concurrent.CallableDistributor;
import org.threadly.concurrent.PriorityScheduledExecutor;
import org.threadly.concurrent.TaskPriority;
import org.threadly.concurrent.VirtualRunnable;
import org.threadly.util.ExceptionUtils;

public class ThreadlyFractal {
  private static final BigDecimal windowWidth = getBigDecimal(Toolkit.getDefaultToolkit().getScreenSize().width);
  private static final BigDecimal windowHeight = getBigDecimal(Toolkit.getDefaultToolkit().getScreenSize().height);
  private static final PriorityScheduledExecutor scheduler;
  private static final CallableDistributor<BigDecimal, int[]> cd;
  
  static {
    int processors = Runtime.getRuntime().availableProcessors();
    scheduler = new PriorityScheduledExecutor(processors * 2, processors * 2, 
                                              1000, TaskPriority.High, 500);
    cd = new CallableDistributor<BigDecimal, int[]>(processors * 2, scheduler);
  }
  
  private static Image image;
  private static BigDecimal fractalWidth = windowWidth;
  private static BigDecimal fractalHeight = windowHeight;
  private static BigDecimal xOffset = getBigDecimal(0);
  private static BigDecimal yOffset = getBigDecimal(0);
  
  public static void main(String[] args) throws Exception {
    displayFractal();
  }
  
  private static void displayFractal() {
    updateImage();

    Frame frame = new Frame("Fractal");
    frame.add(new FractalCanvas(frame));
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    frame.setSize(windowWidth.intValue(), 
                  windowHeight.intValue());
    
    frame.setVisible(true);
  }
  
  private static void updateImage() {
    System.out.println("Generating image...Size: " + fractalWidth + "x" + fractalHeight + 
                         ", Position: " + yOffset + "x" + xOffset);
    
    int[] imageData = new int[windowWidth.multiply(windowHeight).intValue()];
    for (BigDecimal y = yOffset; y.compareTo(windowHeight.add(yOffset)) < 0; y.add(BigDecimal.ONE)) {
      final BigDecimal f_y = y;
      cd.submit(y, new Callable<int[]>() {
        @Override
        public int[] call() {
          int[] result = new int[windowWidth.intValue()];
          int index = 0;
          for (BigDecimal x = xOffset; x.compareTo(windowWidth.add(xOffset)) < 0; x = x.add(BigDecimal.ONE)) {
            result[index] = MandelbrotFractal.calculatePixel(x, f_y, fractalWidth, fractalHeight, 
                                                             0xFF000000);
            // create a little background
            double a = Math.sqrt(x.multiply(windowWidth.divide(fractalWidth)).doubleValue());
            double b = Math.sqrt(x.multiply(windowHeight.divide(fractalHeight)).doubleValue());
            result[index++] += (int) (a + b);
          }
          int percentDone = (int)(f_y.subtract(yOffset).divide(windowHeight).doubleValue() * 100);
          // little extra check to avoid reporting multiple times due to int precision
          if (percentDone != (int)(f_y.subtract(yOffset.add(BigDecimal.ONE)).divide(windowHeight).doubleValue() * 100)) {
            if (percentDone % 10 == 0) {
              System.out.println(percentDone + " % done");
            }
          }
          
          return result;
        }
      });
    }

    for (BigDecimal y = yOffset; y.compareTo(windowHeight.add(yOffset)) < 0; y = y.add(BigDecimal.ONE)) {
      int indexStart = y.subtract(yOffset).multiply(windowWidth).intValue();
      int[] result;
      try {
        result = cd.getNextResult(y).get();
      } catch (ExecutionException e) {
        throw ExceptionUtils.makeRuntime(e);
      } catch (InterruptedException e) {
        throw ExceptionUtils.makeRuntime(e);
      }
      System.arraycopy(result, 0, imageData, indexStart, result.length);
    }
    
    System.out.println("Done generating fractal");
    
    image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(windowWidth.intValue(), windowHeight.intValue(), 
                                                                          imageData, 0, windowWidth.intValue()));
  }
  
  private static BigDecimal getBigDecimal(double val) {
    return new BigDecimal(val, MathContext.UNLIMITED);
  }

  private static class FractalCanvas extends Canvas 
                                     implements MouseListener {
    private static final long serialVersionUID = -2909907873906146984L;

    private final Frame frame;
    private Point pressedPoint;
    
    private FractalCanvas(Frame frame) {
      this.frame = frame;
      pressedPoint = null;
      
      addMouseListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getButton() == 3) { // reset image
        frame.setVisible(false);
        
        fractalWidth = windowWidth;
        fractalHeight = windowHeight;
        xOffset = getBigDecimal(0);
        yOffset = getBigDecimal(0);

        displayFractal();
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (e.getButton() == 1) {
        pressedPoint = e.getPoint();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (e.getButton() == 1) {
        frame.setVisible(false);
        
        final int startPointX = pressedPoint.x;
        final int startPointY = pressedPoint.y;
        final int endPointX = e.getPoint().x;
        final int endPointY = e.getPoint().y;
        
        scheduler.execute(new VirtualRunnable() {
          @Override
          public void run() {
            // calculate selected distances based off total image size
            BigDecimal xDistance = getBigDecimal(Math.abs(endPointX - startPointX)).multiply(fractalWidth.divide(windowWidth));
            BigDecimal yDistance = getBigDecimal(Math.abs(endPointY - startPointY)).multiply(fractalHeight.divide(windowHeight));
            if (xDistance.compareTo(BigDecimal.ZERO) == 0 || yDistance.compareTo(BigDecimal.ZERO) == 0) {
              System.out.println("Section too small, ignoring zoom");
              displayFractal();
              return;
            }
            
            // figure out how to scale all our values
            BigDecimal aspect = windowWidth.divide(windowHeight);
            BigDecimal selectedAspect = xDistance.divide(yDistance);
            BigDecimal scaleFactor;
            if (selectedAspect.compareTo(aspect) > 0) {  // depend on x
              scaleFactor = fractalWidth.divide(xDistance);
            } else {  // depend on y
              scaleFactor = fractalHeight.divide(yDistance);
            }
            
            // update values based off scale factory
            fractalWidth = fractalWidth.multiply(scaleFactor);
            fractalHeight = fractalHeight.multiply(scaleFactor);
            BigDecimal xOffsetPoint = getBigDecimal(startPointX < endPointX ? startPointX : endPointX);
            BigDecimal yOffsetPoint = getBigDecimal(startPointY < endPointY ? startPointY : endPointY);
            xOffset = xOffset.add(xOffsetPoint).multiply(scaleFactor);
            yOffset = yOffset.add(yOffsetPoint).multiply(scaleFactor);
            
            displayFractal();
          }
        });
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      //ignored
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // ignored
    }
    
    @Override
    public void paint(Graphics g) {
      g.drawImage(image, 0, 0, null);
    }
  }
}