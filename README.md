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


## Why

If there are simple examples showing authentication with robust scenario handling such
as the above, then I can't find them.

