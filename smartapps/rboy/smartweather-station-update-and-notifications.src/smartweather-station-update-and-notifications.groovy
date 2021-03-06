/*
 * -----------------------
 * ------ SMART APP ------
 * -----------------------
 *
 * STOP:  Do NOT PUBLISH the code to GitHub, it is a VIOLATION of the license terms.
 * You are NOT allowed share, distribute, reuse or publicly host (e.g. GITHUB) the code. Refer to the license details on our website.
 *
 */

/* **DISCLAIMER**
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
* HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
* Without limitation of the foregoing, Contributors/Regents expressly does not warrant that:
* 1. the software will meet your requirements or expectations;
* 2. the software or the software content will be free of bugs, errors, viruses or other defects;
* 3. any results, output, or data provided through or generated by the software will be accurate, up-to-date, complete or reliable;
* 4. the software will be compatible with third party software;
* 5. any errors in the software will be corrected.
* The user assumes all responsibility for selecting the software and for the results obtained from the use of the software. The user shall bear the entire risk as to the quality and the performance of the software.
*/ 
 
def clientVersion() {
    return "02.01.02"
}

/**
 *  Weather Station Controller
 *
 * Copyright RBoy Apps
 * 2020-01-20 - (v04.04.03) Update icons for broken ST Android app 2.18
 * 2019-10-11 - (v02.01.01) Add support for the new Sonos integration (auto detect)
 * 2019-09-05 - (v02.00.00) Add support for severe weather notifications with option to not repeat notifications
 * 2018-01-23 - (v01.08.00) Updates for new ST weather tile
 * 2017-10-13 - (v01.07.03) Patch for Android 2.7.0 app broken causing error
 * 2017-10-09 - (v01.07.02) Patch for platform update
 * 2017-10-03 - Delayed check to avoid error
 * 2017-05-26 - Added automatic update notifications
 * 2016-03-03 - Set to use runEvery5Minutes and hopefully more reliable
 * 2016-02-12 - Changed scheduling API's (hopefully more resilient), added an option for users to specify update interval
 * 2016-01-20 - Kick start timers on sunrise and sunset also
 * 2015-10-04 - Kick start timers on each mode change to prevent them from dying
 * 2015-07-12 - Simplified app, udpates every 5 minutes now (hopefully more reliable)
 * 2015-07-17 - Improved reliability when mode changes
 * 2015-06-06 - Bugfix for timers not scheduling, keep only one timer
 * Added support to update multiple devices
 * Added support for frequency of updates            
*/

definition(
    name: "SmartWeather Station Update and Notifications",
    namespace: "rboy",
    author: "RBoy Apps",
    description: "Updates SmartWeather Station Tile devices every 5 minutes and send notifications",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-MindYourHome.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-MindYourHome@2x.png"
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    log.trace "$settings"

    dynamicPage(name: "mainPage", title: "SmartWeather Station Tile Updater v${clientVersion()}", install: true, uninstall: true) {
        section ("Weather Devices") {
            input "weatherDevices", "device.smartweatherStationTile", title: "Select Weather Device(s)", description: "Select the Weather Tiles to update", required: true, multiple: true
            //input name: "updateInterval", type: "number", title: "Enter update frequency (minutes)", description: "How often do you want to update the weather information", range: "1..*", required: true, defaultValue: 5
        }

        section("Notifications") {
            input "repeatNotification", "bool", title: "Repeat weather alert notifications every hour", defaultValue: false, required: false
            input "audioDevices", "capability.audioNotification", title: "Speak notifications on", required: false, multiple: true, submitOnChange: true, image: "https://www.rboyapps.com/images/Horn.png"
            if (audioDevices) {
                input "audioVolume", "number", title: "...at this volume level (optional)", description: "keep current", required: false, range: "1..100"
            }
            input("recipients", "contact", title: "Send notifications to", multiple: true, required: false) {
                paragraph "You can enter multiple phone numbers by separating them with a '*'. E.g. 5551234567*+18747654321"
                input "sms", "phone", title: "Send SMS notification to", required: false, image: "https://www.rboyapps.com/images/Notifications.png"
                input "push", "bool", title: "Send push notifications", defaultValue: true, required: false
            }
        }

        section("Advanced Settings") {
            mode title: "Select operating mode(s)"
            label title: "Assign a name for this SmartApp (optional)", required: false
            input name: "updateNotifications", title: "Check for app updates", type: "bool", defaultValue: true, required: false
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    initialize()
}

def initialize() {
    unsubscribe()
    unschedule()

    state.clientVersion = clientVersion() // Update our local stored client version to detect code upgrades
    
    state.notificationsList = [:] // Reset the list

    // Check for new versions of the code
    def random = new Random()
    Integer randomHour = random.nextInt(18-10) + 10
    Integer randomDayOfWeek = random.nextInt(7-1) + 1 // 1 to 7
    schedule("* 0 " + randomHour + " ? * " + randomDayOfWeek, checkForCodeUpdate) // Check for code updates once a week at a random day and time between 10am and 6pm

    subscribe(location, modeChangeHandler)
    subscribe(app, modeChangeHandler)
    
    log.trace "Refresh weather tile update frequency 5 minutes and severe weather alerts every 60 minutes"
    runEvery5Minutes(scheduledEvent)
    runEvery1Hour(checkForSevereWeather)
    runIn(1, scheduledEvent)
    runIn(10, checkForSevereWeather) // Give it time to update
}

def modeChangeHandler(evt) {
    log.debug "Reinitializing refresh timers on mode change notification, new mode $evt.value"
    runIn(1, scheduledEvent)
    runIn(10, checkForSevereWeather) // Give it time to update
}

def scheduledEvent() {
    // Check if the user has upgraded the SmartApp and reinitailize if required
    if (state.clientVersion != clientVersion()) {
        def msg = "NOTE: ${app.label} detected a code upgrade. Updating configuration, please open the app and click on Save to re-validate your settings"
        log.warn msg
        runIn(1, initialize) // Reinitialize the app offline to avoid a loop as appTouch calls codeCheck
        sendPush(msg) // Do this in the end as it may timeout
        return
    }

    log.trace "Refreshing weather state"
    weatherDevices*.refresh()
}

def checkForSevereWeather() {
    log.trace "Checking for weather notifications"
    
    if(!locationIsDefined()) {
        def msg = "Hub geolocation not set. Use the SmartThings app to set the Hub geolocation to get the correct weather notifications."
        log.error msg
        sendPush msg
        return
    }
    
    def alerts = getTwcAlerts("${location.latitude},${location.longitude}")
    log.trace "${alerts?.inspect()}\n${state.notificationsList}"

    // Stop tracking expired notifications
    def nList = state.notificationsList
    nList.each { item ->
        if (item.value.expireTimeUTC < ((now()/1000) as Long)) { // Convert from ms to s
            log.trace "Expired notification $item"
            state.notificationsList.remove(item.key)
        }
    }

    if (alerts) {
        alerts.each {alert ->
            def msg = alert.headlineText
            if (alert.effectiveTimeLocal && !msg.contains(" from ")) {
                msg += " from ${parseAlertTime(alert.effectiveTimeLocal).format("E hh:mm a", TimeZone.getTimeZone(alert.effectiveTimeLocalTimeZone))}"
            }
            if (alert.expireTimeLocal && !msg.contains(" until ")) {
                msg += " until ${parseAlertTime(alert.expireTimeLocal).format("E hh:mm a", TimeZone.getTimeZone(alert.expireTimeLocalTimeZone))}"
            }
            
            if (repeatNotification || !state.notificationsList[alert.issueTimeLocal]) {
                log.info msg
                sendNotifications(msg)
            } else {
                log.debug "Skipping notification: $msg"
            }

            // Successful, track it
            if (alert.issueTimeLocal) {
                state.notificationsList[alert.issueTimeLocal] = [expireTimeUTC: alert.expireTimeUTC]
            } else {
                log.error "Cannot track incomplete alert: $alert"
            }
        }
    } else {
        log.info "No current alerts"
    }
}

def descriptionFilter(String description) {
    def filterList = ["special", "statement", "test"]
    def passesFilter = true
    filterList.each() { word ->
        if(description.toLowerCase().contains(word)) { passesFilter = false }
    }
    passesFilter
}

def locationIsDefined() {
    (location.latitude && location.longitude)
}

def zipcodeIsValid(zipCode = location.zipCode) {
    zipCode && zipCode.isNumber() && zipCode.size() == 5
}

private void sendText(number, message) {
    if (number) {
        def phones = number.replaceAll("[;,#]", "*").split("\\*") // Some users accidentally use ;,# instead of * and ST can't handle *,#+ in the number except for + at the beginning
        for (phone in phones) {
            try {
                sendSms(phone, message)
            } catch (Exception e) {
                sendPush "Invalid phone number $phone"
            }
        }
    }
}

private void sendNotifications(message, uri = null) {
	if (!message) {
		return
    }
    
    if (location.contactBookEnabled) {
        sendNotificationToContacts(message, recipients)
    } else {
        if (push) {
            sendPush message
        } else {
            //sendNotificationEvent(message)
        }
        if (sms) {
            sendText(sms, message)
        }
    }

    if (uri && audioDevices) { // Play audio notifications
        if (audioVolume) { // Only set volume if defined as it also resumes playback
            audioDevices*.playTrackAndResume(uri, audioVolume)
        } else {
            audioDevices*.playTrack(uri)
        }
    } else if (audioDevices) { // Speak audio notifications
        audioDevices?.each { audioDevice -> // Play audio notifications
            if (audioDevice.hasCommand("playText")) { // Check if it supports TTS
                if (audioVolume) { // Only set volume if defined as it also resumes playback
                    audioDevice.playTextAndResume(message, audioVolume)
                } else {
                    audioDevice.playText(message)
                }
            } else {
                if (audioVolume) { // Only set volume if defined as it also resumes playback
                    audioDevice.playTrackAndResume(textToSpeech(message)?.uri, audioVolume) // No translations at this time
                } else {
                    audioDevice.playTrack(textToSpeech(message)?.uri) // No translations at this time
                }
            }
        }
    }
}

def checkForCodeUpdate(evt) {
    log.trace "Getting latest version data from the RBoy Apps server"
    
    def appName = "SmartWeather Station Tile Updater"
    def serverUrl = "http://smartthings.rboyapps.com"
    def serverPath = "/CodeVersions.json"
    
    try {
        httpGet([
            uri: serverUrl,
            path: serverPath
        ]) { ret ->
            log.trace "Received response from RBoy Apps Server, headers=${ret.headers.'Content-Type'}, status=$ret.status"
            //ret.headers.each {
            //    log.trace "${it.name} : ${it.value}"
            //}

            if (ret.data) {
                log.trace "Response>" + ret.data
                
                // Check for app version updates
                def appVersion = ret.data?."$appName"
                if (appVersion > clientVersion()) {
                    def msg = "New version of app ${app.label} available: $appVersion, current version: ${clientVersion()}.\nPlease visit $serverUrl to get the latest version."
                    log.info msg
                    if (updateNotifications != false) { // The default true may not be registered
                        sendPush(msg)
                    }
                } else {
                    log.trace "No new app version found, latest version: $appVersion"
                }
                
                // Check device handler version updates
                def caps = []
                caps?.each {
                    def devices = it?.findAll { it.hasAttribute("codeVersion") }
                    for (device in devices) {
                        if (device) {
                            def deviceName = device?.currentValue("dhName")
                            def deviceVersion = ret.data?."$deviceName"
                            if (deviceVersion && (deviceVersion > device?.currentValue("codeVersion"))) {
                                def msg = "New version of device ${device?.displayName} available: $deviceVersion, current version: ${device?.currentValue("codeVersion")}.\nPlease visit $serverUrl to get the latest version."
                                log.info msg
                                if (updateNotifications != false) { // The default true may not be registered
                                    sendPush(msg)
                                }
                            } else {
                                log.trace "No new device version found for $deviceName, latest version: $deviceVersion, current version: ${device?.currentValue("codeVersion")}"
                            }
                        }
                    }
                }
            } else {
                log.error "No response to query"
            }
        }
    } catch (e) {
        log.error "Exception while querying latest app version: $e"
    }
}

// THIS IS THE END OF THE FILE