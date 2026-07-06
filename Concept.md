Android TV Wheel Keyboard Specification

Project Overview

The TV Wheel Keyboard is a new keyboard concept designed specifically for Android TV applications that are controlled using a standard D-pad remote.

Instead of using a traditional grid or horizontal keyboard that requires users to constantly move in four directions, this keyboard introduces a single continuous wheel that contains every selectable item.

The keyboard is primarily intended for applications where search is the main interaction, such as streaming apps, IPTV apps, media centers, YouTube clients, and file browsers.

Its main design goal is to make text entry and text editing feel simple, predictable, and effortless using only a TV remote.


---

Core Philosophy

The keyboard has one navigation component only: the wheel.

Everything the user can select exists on this wheel.

The user never has to leave the wheel to perform another action.

Instead of moving focus between different parts of the interface, the user always stays on the wheel while editing the text using the remote.

This creates a consistent and easy-to-learn interaction model.


---

Wheel Layout

The keyboard is presented as a circular wheel (ring).

The wheel contains every selectable action in one continuous loop.

The wheel includes:

Letters A through Z

Numbers 0 through 9

Space

Delete (⌫)

Search (🔍)

Voice Search (🎤)

Done/Enter (✓) (optional)

Clear Text (🗑️) (optional)


These items exist in one continuous sequence.

For example:

A → B → C → D → ... → Z → 0 → 1 → 2 → ... → 9
→ Space → Delete → Search → Voice Search
→ Done → Clear Text → A

There is no beginning and no end.

The wheel wraps infinitely in both directions.


---

Search Field

Above the wheel is a search field.

The search field displays:

The current text.

A blinking text cursor.


The search field is not focusable.

It is only a visual display of the user's input.

The cursor is controlled directly using the remote without changing focus away from the wheel.


---

Remote Controls

Up Button

Moves one position backward around the wheel.


---

Down Button

Moves one position forward around the wheel.


---

Left Button

Moves the text cursor one character to the left.

It never changes the wheel selection.


---

Right Button

Moves the text cursor one character to the right.

It never changes the wheel selection.


---

OK / Select Button

Activates the currently selected item on the wheel.

Examples:

Selecting a letter inserts or replaces that letter.

Selecting a number inserts or replaces that number.

Selecting Space inserts a space.

Selecting Delete deletes the character immediately before the cursor.

Selecting Search performs the application's search.

Selecting Voice Search activates speech recognition and inserts the recognized speech into the search field.

Selecting Done closes the keyboard.

Selecting Clear Text removes all text from the search field.


---

Text Editing

One of the defining features of this keyboard is that typing and editing happen at the same time.

The user never has to leave the wheel to edit text.

Example:

Current text:

MAREZ OF EASTTOWN

The user notices that "Z" should be "S".

Instead of moving focus away from the keyboard:

Press Left until the cursor reaches the incorrect letter.

Rotate the wheel until "S" is selected.

Press OK.


The incorrect character is replaced immediately.

The wheel remains active throughout the entire editing process.


---

Character Insertion

When the cursor is between two characters:

MAR|E

Selecting "T" results in:

MART|E


---

Character Replacement

When the cursor is positioned on an existing character:

MARE
   ^

Selecting another character replaces the existing one.

This allows quick correction of typing mistakes without deleting and retyping entire words.


---

Wheel Behavior

The wheel should feel responsive and satisfying.

Recommended behavior includes:

Smooth animations.

Infinite wrap-around scrolling.

One item always selected.

Subtle scaling or highlighting of the selected item.

Optional haptic feedback where supported.



---

Visual Design

The currently selected wheel item should always be obvious.

Possible indicators include:

Increased size.

Bright highlight.

Glow.

Accent color.

Slight zoom animation.


The rest of the wheel remains visible so users always know what comes next.


---

Voice Search

Voice Search is treated exactly like every other wheel item.

When selected:

The microphone opens.

Speech recognition begins.

The recognized speech is inserted into the search field.

The user may continue editing using the wheel if needed.


Voice Search is not a separate button or menu.

It is simply another position on the wheel.


---

Delete

Delete is also part of the wheel.

When selected:

Deletes the character immediately before the cursor.


Optional behavior:

Holding the OK button continuously deletes characters until released.


---

Search

Search is another wheel item.

When selected:

The keyboard closes (if required by the application).

The entered text is submitted to the application.

The search is performed.



---

Design Principles

The keyboard is designed around five core principles:

1. One navigation system. Every selectable action is on the same wheel.


2. Separate responsibilities. Up and Down rotate the wheel. Left and Right move the text cursor. These controls never change roles.


3. Continuous interaction. Users never leave the keyboard to edit text or access commands.


4. Infinite scrolling. The wheel has no beginning or end, allowing continuous navigation.


5. Minimal cognitive load. The interaction model is simple, predictable, and easy to learn.




---

Target Applications

This keyboard is intended for:

Streaming platforms.

Android TV media players.

IPTV applications.

Video-on-demand services.

YouTube clients.

Music applications.

File browsers.

Any Android TV application requiring text input.



---

Vision

The TV Wheel Keyboard is not simply another keyboard layout. It introduces a new interaction model for Android TV by treating every action—letters, numbers, space, delete, search, voice search, and other commands—as equal members of one continuous wheel. Users stay on that wheel at all times, while the Left and Right buttons are dedicated exclusively to cursor movement within the text. The result is a keyboard that is simple, consistent, efficient, and specifically designed for the realities of TV remote input.
