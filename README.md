# Trivia-Winner

# How it works

-First, takes a screenshot of the whole screen, and crops it for the question and options regions.

-Next, uses an OCR (google mobile vision api) to read the text from the question and options image.

-Then, it google search the question and counts the occurences of each option in the 10 first results of the search.

# How to use:

-Download and install 'TriviaWinner.apk' from the repository.

-Select game.

-Press the icon to scan the screen.

-After a few seconds, a message will apear in the bottom of the screen.

-The message shows the ocurrence of each option in a google search.

# Details:

-The screenshots taken are in the files of your device under the directory 'Captures'. Check them if the scan is not working properly.

-Hangtime must be played in horizontal mode.

-When is a 'not' question try going for the option with less or 0 ocurrences.

-App is more accurate with questions that are easy to google, so try to make your own choice.

# Permissions

-Draw Overlay - To take the screenshot

-Storage - To store the screenshot

-Internet - To the google search

# Games supported

-HQ

-Cash Show (US/UK/AUS/FR/GER)

-Hangtime

-Q12

-Hypsports

-TheQ
