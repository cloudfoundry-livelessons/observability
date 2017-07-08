#!/bin/bash

find . -iname pom.xml | xargs -Ipom mvn -f pom clean install

cf cs cloudamqp lemur autoscaler-rabbit

cd consumer
cf push
cd ..

cd producer
cf push
cd ..

cd rabbit-queue-autoscaler
cf push --no-start

# autoscaler vars
cf set-env rabbit-queue-autoscaler autoscaler_rabbitMq_applicationName rmq-consumer
cf set-env rabbit-queue-autoscaler autoscaler_rabbitMq_queueName autoscaler-demo.autoscaler-demo-group

# CF vars
cf set-env rabbit-queue-autoscaler CF_API ${CF_API}
cf set-env rabbit-queue-autoscaler CF_ORG ${CF_ORG}
cf set-env rabbit-queue-autoscaler CF_PASSWORD ${CF_PASSWORD}
cf set-env rabbit-queue-autoscaler CF_SPACE ${CF_SPACE}
cf set-env rabbit-queue-autoscaler CF_USER ${CF_USER}

cf start rabbit-queue-autoscaler
cd ..
