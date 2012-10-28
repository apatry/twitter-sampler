# Twitter Sampler

I wanted to play around with a corpus of tweets, but none is openly
distributed due to twitter terms of services. So I built this utility
to create my own.

## Usage

This program uses twitter streaming api, which can only be accessed by
authenticated user. In order to create your credentials, you must
create a twitter application using https://dev.twitter.com/apps/new.

Once your application is created, you can doownland a
[sample configuration file](https://raw.github.com/apatry/twitter-sampler/master/credentials.clj)
and fill in the blanks. You can then
[download twitter sampler](https://github.com/downloads/apatry/twitter-sampler/twitter-sampler-1.0.0-SNAPSHOT-standalone.jar)
and run it with the following command to download 1000 tweets in the
file `tweets.json`:

	java -jar twitter-sampler-1.0.0-SNAPSHOT-standalone.jar -c credentials.clj -n 1000 > tweets.json

where `credentials.clj` contains your twitter credentials and `-n`
specifies the number of tweets to download.

## How does it work?

This application is built with clojure and uses
[twitter-api](https://github.com/adamwynne/twitter-api) to download
tweets from the
[`/statuses/sample`](https://dev.twitter.com/docs/api/1.1/get/statuses/sample)
endpoint of the twitter api.

## License

Copyright (C) 2012 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
