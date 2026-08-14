FROM alpine:3.18
RUN apk add --no-cache curl bash
WORKDIR /app
COPY send_work.sh .
RUN chmod +x send_work.sh
CMD ["bash", "send_work.sh", "job-compose-test", "SEND_EMAIL"]
