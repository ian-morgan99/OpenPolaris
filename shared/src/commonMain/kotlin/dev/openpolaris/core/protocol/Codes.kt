package dev.openpolaris.core.protocol

/** Command codes from PROTOCOL.md §3. Single source of truth — never use raw ints elsewhere. */
object Codes {
    const val PUSH_MODE_STATE = 284
    const val GET_GIMBAL_POS = 517
    const val PUSH_ROTATE_VECTOR = 518
    const val SET_GOTO_AU_STATE = 519
    const val SET_AHRS_STATE = 520
    const val GIMBAL_HADJ_SPEED = 513
    const val GIMBAL_VADJ_SPEED = 514
    const val GIMBAL_HADJ_ANGLE = 515
    const val GIMBAL_VADJ_ANGLE = 516
    const val GIMBAL_RADJ_SPEED = 521
    const val GIMBAL_RADJ_ANGLE = 522
    const val POS_RESET = 523
    const val EX_AXIS_STA = 524
    const val YAW_KEY = 532
    const val PITCH_KEY = 533
    const val ROLL_KEY = 534
    const val SET_GIMBAL_POS = 535
    const val CALIBRATE_START = 530
    const val SET_TRACK_AU_STATE = 531
    const val SET_TRACK_HALF_SPEED = 536
    const val GET_TILT_STATE = 537
    const val SET_TILT_STATE = 538
    const val GET_DITHER_STATE = 539
    const val SET_DITHER_STATE = 540
    const val GET_LIMIT_STATE = 541
    const val SET_LIMIT_STATE = 542
    const val GET_SETTLING_TIME = 543
    const val SET_SETTLING_TIME = 544
    const val GET_AUTO_LEVEL_EN = 547
    const val SET_AUTO_LEVEL_EN = 548
    const val SET_AUTO_LEVEL_STATE = 549

    // Camera parameter family (258–311), get/set pairs.
    //
    // IMPORTANT: the exact numeric codes below are INFERRED, not confirmed. The firmware
    // builds them dynamically (no literals in polestar_app) and the APK is unavailable.
    // What IS ground truth (from polestar_app strings): the payload formats and the
    // index-based set semantics (SP_SetCameraIsoIndex etc.). Keep all camera controls
    // disabled until validated on hardware; see PROTOCOL.md §3.4.
    const val CAMERA_BASE = 258
    const val CAMERA_END = 311

    const val CAM_GET_ISO = 258
    const val CAM_SET_ISO = 259
    const val CAM_GET_WB = 260
    const val CAM_SET_WB = 261
    const val CAM_GET_FNUM = 262
    const val CAM_SET_FNUM = 263
    const val CAM_GET_EV = 264
    const val CAM_SET_EV = 265
    const val CAM_GET_STATE = 266
    const val CAM_CAPTURE = 267
    const val CAM_GET_FOCUS = 268
    const val CAM_SET_DIR = 269
}

/** Literal payload used when a command carries no content. */
const val EMPTY_CONTENT = "-100"

/** Request type marker observed for every command in the stock app. */
const val REQUEST_TYPE = 2
