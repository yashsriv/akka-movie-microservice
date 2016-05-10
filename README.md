# Akka HTTP microservice example

Based off [Akka HTTP Microservice](http://github.com/theiterators/akka-http-microservice) by @theiterators  
Modified so that the request sent to the api can contain URI queries for example:  
`http://www.omdbapi.com/?i=tt0109830&r=json`

## Usage
Code can be executed by opening up `sbt` in the root directory
and `run`.

``` scala
$ sbt
> run
```

Once the code starts running, it can be stopped by pressing enter

By default it runs a server at `http://localhost:8080` which can be queried
for movies via `imdbId` like this:

``` shell
$ curl http://localhost:8080/movie/tt0109830

{
  "Title": "Forrest Gump",
  "Year": "1994",
  "Director": "Robert Zemeckis",
  "Plot": "Forrest Gump, while not intelligent, has accidentally been present at many historic moments, but his true love, Jenny Curran, eludes him.",
  "imdbID": "tt0109830",
  "Actors": "Tom Hanks, Rebecca Williams, Sally Field, Michael Conner Humphreys"
}<Paste>
```

It also supports `JSON` encoded `imdbID`:

``` shell
$ curl -X GET -H 'Content-Type: application/json' http://localhost:8080/movie -d '{"imdbID": "tt0111161"}'

{
  "Title": "The Shawshank Redemption",
  "Year": "1994",
  "Director": "Frank Darabont",
  "Plot": "Two imprisoned men bond over a number of years, finding solace and eventual redemption through acts of common decency.",
  "imdbID": "tt0111161",
  "Actors": "Tim Robbins, Morgan Freeman, Bob Gunton, William Sadler"
}
```
