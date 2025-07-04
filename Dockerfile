#Build:  docker build -t nassiesse/simple-java-ocr .
#Run: docker run -t -i -p 8080:8080 nassiesse/simple-java-ocr

FROM gradle:8.11.1-jdk21
RUN apt update -y && apt upgrade -y
#RUN apt-get install openjdk-21-jdk -y
# Install tesseract library
RUN apt install libleptonica-dev tesseract-ocr -y

# Install fonts
ENV DEBIAN_FRONTEND noninteractive
ENV DEBIAN_FRONTEND teletype
RUN apt install ttf-mscorefonts-installer fonts-liberation -y --no-install-recommends
RUN fc-cache -fv

# Download last language package
RUN mkdir -p /usr/share/tessdata
ADD https://github.com/tesseract-ocr/tessdata/raw/refs/heads/main/eng.traineddata /usr/share/tessdata/eng.traineddata

# Check the installation status
RUN tesseract --list-langs    
RUN tesseract -v

WORKDIR /build

COPY . .

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
# don't use gradle wrapper
RUN gradle bootJar

# Open the port
EXPOSE 8080

# Copy our JAR
RUN cp build/libs/*.jar /app.jar

# Launch the Spring Boot application
ENV JAVA_OPTS="-Dspring.profiles.active=prod"
CMD [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
#CMD ["sleep", "infinity"]
