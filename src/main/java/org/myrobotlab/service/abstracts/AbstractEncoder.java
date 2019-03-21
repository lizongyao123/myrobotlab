package org.myrobotlab.service.abstracts;

import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.ServiceType;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.sensor.EncoderData;
import org.myrobotlab.service.interfaces.EncoderControl;
import org.myrobotlab.service.interfaces.EncoderController;

public class AbstractEncoder extends Service implements EncoderControl {

  private static final long serialVersionUID = 1L;
  public Integer pin;
  // default 12 bit encoder is 4096 steps of resolution
  public Integer resolution = 4096;
  public Double lastPosition = 0.0;
  public EncoderController controller = null;
  // we can track the last update that we've recieved and specify the direction
  // even!
  protected long lastUpdate = 0;
  protected Double velocity = 0.0;

  public AbstractEncoder(String reservedKey) {
    super(reservedKey);
  }

  @Override
  public void attach(EncoderController controller, Integer pin) throws Exception {
    this.controller = controller;
    this.pin = pin;
    controller.attach(this, pin);
    lastUpdate = System.currentTimeMillis();
  }

  static public ServiceType getMetaData() {
    ServiceType meta = new ServiceType(AbstractEncoder.class.getCanonicalName());
    meta.addDescription("AMT203 Encoder - Absolute position encoder");
    meta.addCategory("encoder", "sensor");
    return meta;
  }

  @Override
  public int getPin() {
    //
    return pin;
  }

  public Double publishEncoderAngle(Double angle) {
    return angle;
  }

  @Override
  public void onEncoderData(EncoderData data) {
    // this is getting published from the arduino and updated here when it comes
    // in..
    // TODO: shoudl the messaging be setup differently?
    // TODO: compare with ultrasonic sensor and see that we're following the
    // same pattern
    // TODO: maybe use nanoTime? how accurate is this.
    long now = System.currentTimeMillis();
    long delta = now - lastUpdate;
    Double angle = 360.0 * data.value / resolution;
    if (delta > 0) {
      // we can compute velocity since the last update
      // This computes the change in degrees per second that the encoder is
      // currently moving at.
      velocity = (angle - this.lastPosition) / delta * 1000.0;
    } else {
      // no position update since the last tick.
      velocity = 0.0;
    }
    // update the previous values
    this.lastPosition = angle;
    this.lastUpdate = now;
    log.info("Encoder Data : {} Angle : {}", data, lastPosition);
  }

  public void setZeroPoint() {
    // pass the set zero point command to the controller
    controller.setZeroPoint(this);
  }

  @Override
  public void setController(EncoderController controller) {
    // TODO Auto-generated method stub
    this.controller = controller;
  }

}
