FROM vertx/vertx3

ENV VERTICLE_HOME /usr/verticles
ENV VERTICLE_NAME com.yofc.iot.docker.app.MqttOracle

COPY ./verticles $VERTICLE_HOME

ENTRYPOINT ["sh", "-c"]
CMD ["exec vertx run $VERTICLE_NAME -cp $VERTICLE_HOME/*"]