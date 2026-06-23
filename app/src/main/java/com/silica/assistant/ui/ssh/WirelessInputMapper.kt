package com.silica.assistant.ui.ssh

import android.view.KeyEvent

object WirelessInputMapper {

    fun keyCodeToXdotool(keyCode: Int): String? {
        return BASIC_MAP[keyCode] ?: MEDIA_MAP[keyCode] ?: EXTRA_MAP[keyCode]
    }

    fun isModifierKey(keyCode: Int): Boolean = keyCode in MODIFIER_KEYS

    fun getClickAction(buttonState: Int): String? {
        return when {
            buttonState and 0x01 != 0 -> "1"
            buttonState and 0x02 != 0 -> "3"
            buttonState and 0x04 != 0 -> "2"
            else -> null
        }
    }

    fun getButtonName(button: Int): String? {
        return when (button) {
            0x01 -> "1"
            0x02 -> "3"
            0x04 -> "2"
            else -> null
        }
    }

    fun scrollToXdotool(amount: Float): String {
        return if (amount < 0) "5" else "4"
    }

    private val MODIFIER_KEYS = setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT,
        KeyEvent.KEYCODE_META_RIGHT,
        KeyEvent.KEYCODE_CAPS_LOCK,
        KeyEvent.KEYCODE_NUM_LOCK,
        KeyEvent.KEYCODE_SCROLL_LOCK,
    )

    private val BASIC_MAP = mapOf(
        KeyEvent.KEYCODE_A to "a",
        KeyEvent.KEYCODE_B to "b",
        KeyEvent.KEYCODE_C to "c",
        KeyEvent.KEYCODE_D to "d",
        KeyEvent.KEYCODE_E to "e",
        KeyEvent.KEYCODE_F to "f",
        KeyEvent.KEYCODE_G to "g",
        KeyEvent.KEYCODE_H to "h",
        KeyEvent.KEYCODE_I to "i",
        KeyEvent.KEYCODE_J to "j",
        KeyEvent.KEYCODE_K to "k",
        KeyEvent.KEYCODE_L to "l",
        KeyEvent.KEYCODE_M to "m",
        KeyEvent.KEYCODE_N to "n",
        KeyEvent.KEYCODE_O to "o",
        KeyEvent.KEYCODE_P to "p",
        KeyEvent.KEYCODE_Q to "q",
        KeyEvent.KEYCODE_R to "r",
        KeyEvent.KEYCODE_S to "s",
        KeyEvent.KEYCODE_T to "t",
        KeyEvent.KEYCODE_U to "u",
        KeyEvent.KEYCODE_V to "v",
        KeyEvent.KEYCODE_W to "w",
        KeyEvent.KEYCODE_X to "x",
        KeyEvent.KEYCODE_Y to "y",
        KeyEvent.KEYCODE_Z to "z",
        KeyEvent.KEYCODE_0 to "0",
        KeyEvent.KEYCODE_1 to "1",
        KeyEvent.KEYCODE_2 to "2",
        KeyEvent.KEYCODE_3 to "3",
        KeyEvent.KEYCODE_4 to "4",
        KeyEvent.KEYCODE_5 to "5",
        KeyEvent.KEYCODE_6 to "6",
        KeyEvent.KEYCODE_7 to "7",
        KeyEvent.KEYCODE_8 to "8",
        KeyEvent.KEYCODE_9 to "9",
        KeyEvent.KEYCODE_SPACE to "space",
        KeyEvent.KEYCODE_ENTER to "Return",
        KeyEvent.KEYCODE_DEL to "BackSpace",
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_ESCAPE to "Escape",
        KeyEvent.KEYCODE_SHIFT_LEFT to "Shift_L",
        KeyEvent.KEYCODE_SHIFT_RIGHT to "Shift_R",
        KeyEvent.KEYCODE_CTRL_LEFT to "Control_L",
        KeyEvent.KEYCODE_CTRL_RIGHT to "Control_R",
        KeyEvent.KEYCODE_ALT_LEFT to "Alt_L",
        KeyEvent.KEYCODE_ALT_RIGHT to "Alt_R",
        KeyEvent.KEYCODE_META_LEFT to "Super_L",
        KeyEvent.KEYCODE_META_RIGHT to "Super_R",
        KeyEvent.KEYCODE_CAPS_LOCK to "Caps_Lock",
        KeyEvent.KEYCODE_NUM_LOCK to "Num_Lock",
        KeyEvent.KEYCODE_SCROLL_LOCK to "Scroll_Lock",
        KeyEvent.KEYCODE_DPAD_UP to "Up",
        KeyEvent.KEYCODE_DPAD_DOWN to "Down",
        KeyEvent.KEYCODE_DPAD_LEFT to "Left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "Right",
        KeyEvent.KEYCODE_F1 to "F1",
        KeyEvent.KEYCODE_F2 to "F2",
        KeyEvent.KEYCODE_F3 to "F3",
        KeyEvent.KEYCODE_F4 to "F4",
        KeyEvent.KEYCODE_F5 to "F5",
        KeyEvent.KEYCODE_F6 to "F6",
        KeyEvent.KEYCODE_F7 to "F7",
        KeyEvent.KEYCODE_F8 to "F8",
        KeyEvent.KEYCODE_F9 to "F9",
        KeyEvent.KEYCODE_F10 to "F10",
        KeyEvent.KEYCODE_F11 to "F11",
        KeyEvent.KEYCODE_F12 to "F12",
        KeyEvent.KEYCODE_INSERT to "Insert",
        KeyEvent.KEYCODE_FORWARD_DEL to "Delete",
        KeyEvent.KEYCODE_MOVE_HOME to "Home",
        KeyEvent.KEYCODE_MOVE_END to "End",
        KeyEvent.KEYCODE_PAGE_UP to "Page_Up",
        KeyEvent.KEYCODE_PAGE_DOWN to "Page_Down",
        KeyEvent.KEYCODE_BREAK to "Break",
        KeyEvent.KEYCODE_BACK to "",
        KeyEvent.KEYCODE_HOME to "",
        KeyEvent.KEYCODE_MENU to "",
        KeyEvent.KEYCODE_APP_SWITCH to "",
        KeyEvent.KEYCODE_VOLUME_UP to "",
        KeyEvent.KEYCODE_VOLUME_DOWN to "",
        KeyEvent.KEYCODE_CAMERA to "",
    )

    private val MEDIA_MAP = mapOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "XF86AudioPlay",
        KeyEvent.KEYCODE_MEDIA_STOP to "XF86AudioStop",
        KeyEvent.KEYCODE_MEDIA_NEXT to "XF86AudioNext",
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to "XF86AudioPrev",
        KeyEvent.KEYCODE_MEDIA_REWIND to "XF86AudioRewind",
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "XF86AudioForward",
        KeyEvent.KEYCODE_MEDIA_PLAY to "XF86AudioPlay",
        KeyEvent.KEYCODE_MEDIA_PAUSE to "XF86AudioPause",
        KeyEvent.KEYCODE_MEDIA_RECORD to "XF86AudioRecord",
        KeyEvent.KEYCODE_MEDIA_CLOSE to "XF86AudioStop",
        KeyEvent.KEYCODE_MEDIA_EJECT to "XF86Eject",
        KeyEvent.KEYCODE_MUSIC to "XF86AudioPlay",
        KeyEvent.KEYCODE_MUTE to "XF86AudioMute",
        KeyEvent.KEYCODE_VOLUME_MUTE to "XF86AudioMute",
    )

    private val EXTRA_MAP = mapOf(
        KeyEvent.KEYCODE_GRAVE to "grave",
        KeyEvent.KEYCODE_MINUS to "minus",
        KeyEvent.KEYCODE_EQUALS to "equal",
        KeyEvent.KEYCODE_LEFT_BRACKET to "bracketleft",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "bracketright",
        KeyEvent.KEYCODE_BACKSLASH to "backslash",
        KeyEvent.KEYCODE_SEMICOLON to "semicolon",
        KeyEvent.KEYCODE_APOSTROPHE to "apostrophe",
        KeyEvent.KEYCODE_COMMA to "comma",
        KeyEvent.KEYCODE_PERIOD to "period",
        KeyEvent.KEYCODE_SLASH to "slash",
        KeyEvent.KEYCODE_AT to "at",
        KeyEvent.KEYCODE_PLUS to "plus",
        KeyEvent.KEYCODE_STAR to "asterisk",
        KeyEvent.KEYCODE_POUND to "numbersign",
        KeyEvent.KEYCODE_NUMPAD_0 to "KP_0",
        KeyEvent.KEYCODE_NUMPAD_1 to "KP_1",
        KeyEvent.KEYCODE_NUMPAD_2 to "KP_2",
        KeyEvent.KEYCODE_NUMPAD_3 to "KP_3",
        KeyEvent.KEYCODE_NUMPAD_4 to "KP_4",
        KeyEvent.KEYCODE_NUMPAD_5 to "KP_5",
        KeyEvent.KEYCODE_NUMPAD_6 to "KP_6",
        KeyEvent.KEYCODE_NUMPAD_7 to "KP_7",
        KeyEvent.KEYCODE_NUMPAD_8 to "KP_8",
        KeyEvent.KEYCODE_NUMPAD_9 to "KP_9",
        KeyEvent.KEYCODE_NUMPAD_ADD to "KP_Add",
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT to "KP_Subtract",
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY to "KP_Multiply",
        KeyEvent.KEYCODE_NUMPAD_DIVIDE to "KP_Divide",
        KeyEvent.KEYCODE_NUMPAD_DOT to "KP_Decimal",
        KeyEvent.KEYCODE_NUMPAD_COMMA to "KP_Separator",
        KeyEvent.KEYCODE_NUMPAD_ENTER to "KP_Enter",
        KeyEvent.KEYCODE_NUMPAD_EQUALS to "KP_Equal",
        KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN to "KP_Left_Paren",
        KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN to "KP_Right_Paren",
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_SYSRQ to "Print",
        KeyEvent.KEYCODE_BREAK to "Pause",
    )
}
