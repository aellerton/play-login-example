# play-login-example

## Purpose

Demonstrate, at least to myself, how a simple Play application can do authentication
in a robust fashion, meaning it gracefully handles use cases like:

 * A user logs in and the session data is stored server-side, with only a session
   key exposed to cookies (not username, etc)

 * Server-side sessions can expire and disappear and the only impact on the user is
   a new login is required.

 * Clear text JSON is stored in the server-side cache (you can do something more 
   obscure if you feel that is important)

 * Gives control of error scenarios (expired session, bad credentials) to the 
   Controller class.


## Run

    $ ### ensure redis is started (see below)
    $ play
    [play-login-example] $ run
  
Then go to http://localhost:9000/


## Key Points to Notice

The index page at http://localhost:9000/ shows links to:

 * An "old school" POST form for authentication.

   If you use this, the authentication data is not persisted in the server-side
   session store. Rather, it POSTs the whole form to a new page (like it's 1999).
   If authentication succeeds you get a welcome page. If it doesn't, you get the
   form with errors.

   It's not glamorous, nor particularly useful. I'm including it for reference
   only because a lot of the play docs point to doing forms this way.

 * A link to a page that requires authentication to work.

   This is the main event. Read below...

The "authentication protected page" definition looks like this:

    def authenticatedIndex = RequireAuthentication { implicit request: AuthenticatedRequest[AnyContent] =>
      Ok(views.html.authorised(s"You're in!."))
    }

The ``RequireAuthentication`` action does the requisite checks on your server-side
session, which it looks up based on a key in your client session (i.e. your cookies).

If there is a session, it uses that, extracting the user data from JSON.
If there is no session, it invokes ``authenticationRequired`` which, in this case, redirects
the user to a login page:

    override def authenticationRequired[A](request: Request[A]): Future[SimpleResult] = {
      Future.successful(
        Redirect(routes.AjaxLogin.showAjaxLoginForm).withSession("goto" -> request.path)
      )
    }


## Why

If there are simple examples showing authentication with robust scenario handling such
as the above, then I can't find them.

### Why Redis?

As I've used Redis as the cache, you'll need Redis running locally for the session
handling to work. I'm open to changing this, e.g. to use the default ehcache by
default but I've been frustrated with the built in play cache's so have not gone
this route so far.

### Why JSON (Jackson direct, not Play)?

So far, I find the default Play wrapper around Jackson inconvenient. The intermediate
JsValue objects have their place but sometimes I just want to convert a String to a 
Scala object and vice-versa, and Jackson (with the Scala module) does this just fine.

The Play Json module does not seem to work with Scala objects out of the box.
Even if you override the ObjectMapper to get Scala objects to work, converting from 
String back to a typed Scala object is cumbersome at best.


### Why cache (My own, not Play)?

I've had trouble using the default Cache implementation - which to be fair was mostly
me - but aspects of the API nagged me. I wanted Redis but using the plugin uses more
layers and obscured clear intent in some cases (like expiry, serialization).

The breaking point for me was that Redis uses the Serlialization API for everything, so
Strings aren't stored as Strings. Accessing the data in ``redis-cli`` looks like:

    redis 127.0.0.1:6379> get 10e2d2f8-0850-4407-a05e-48a6897db706
    "oos-rO0ABXQAAnt9"

I want a ``JsonCache`` so that's what I wrote. Now in ``redis-cli``:

    redis 127.0.0.1:6379> get 4e472a3d-86d9-4e03-becc-b438eb8ed886
    "{\"user\":{\"name\":\"bob\"}}"


