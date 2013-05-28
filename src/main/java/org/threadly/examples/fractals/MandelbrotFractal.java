package org.threadly.examples.fractals;

import java.math.BigDecimal;
import java.math.MathContext;

public class MandelbrotFractal {
  public static int calculatePixel(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, int offset) {
    int result = offset;
    BigDecimal x0 = width.multiply(getBigDecimal(0.37)).divide(x).subtract(getBigDecimal(2));
    BigDecimal y0 = height.multiply(getBigDecimal(0.4)).divide(y).subtract(getBigDecimal(-1.2));
    
    BigDecimal xx = getBigDecimal(0);
    BigDecimal yy = getBigDecimal(0);
    
    int iteration = 0;
    int max_iterations = 1000;
    
    while (xx.multiply(xx).add(yy.multiply(yy)).compareTo(getBigDecimal(2).multiply(getBigDecimal(2))) <= 0 && 
             iteration < max_iterations) {
      BigDecimal xtemp = xx.multiply(xx).subtract(yy.multiply(yy)).add(x0);
      yy = getBigDecimal(2).multiply(xx).multiply(yy).add(y0);
      
      xx = xtemp;
      
      iteration++;
    }
    
    if (iteration == max_iterations) {
      result = 0;
    } else {
      result += iteration;
    }
    
    return result + offset;
  }
  
  private static BigDecimal getBigDecimal(double val) {
    return new BigDecimal(val, MathContext.UNLIMITED);
  }
}