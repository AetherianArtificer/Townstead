// The head of a hunter lodge. Takes on Oathseekers the player brings, and says little else.

=== greet ===
# label: About the lodge
{~The watch starts at dusk.|The oath is short. Keeping it is the long part.|Keep your door shut after dark.}
+ [I know someone who wants to join.]
    Bring them here, close enough that I can see their face.
    ++ [They're here beside me.]
        ~ act("enlist")
        -> DONE
    ++ [Another time.]
        -> DONE
+ [Nothing for now.]
    -> DONE
