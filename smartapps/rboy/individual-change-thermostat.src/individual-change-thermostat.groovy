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
    return "01.01.01"
}

/**
* Individual Change Thermostat
* Allows you to set single/multiple thermostats to different temp during different days of the week unlimited number of times (one app instance for each change)
*
* Base Code from : Samer Theodossy, bugfixed and enhanced by RBoy Apps
* Changes Copyright RBoy Apps, redistribution of any changes or modified code is not allowed without permission
* 2018-7-30 - (v 01.01.01) Time is a required input
* 2017-5-23 - (v 01.01.00) Added support for automatic update notifications and contact address book and multiple numbers for sms notifications and ability to select modes for operations
* 2016-5-15 - Notify use if timezone/location is missing in setup
* 2015-11-21 - Fixed issue with timezone, now takes timezone from hub
* 2015-6-17 - Fix for changes in ST Platform
* Update - 2014-11-25
*/

// Automatically generated. Make future change here.
definition(
    name: "Individual Change Thermostat",
    namespace: "rboy",
    author: "RBoy Apps",
    description: "Setup unlimited adjustments to the thermostat(s), install this app once for each change you want to make",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@3x.png"
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Individual Change Thermostat v${clientVersion()}", install: true, uninstall: true) {
        section("Set these thermostats") {
            input "thermostat", "capability.thermostat", title: "Which?", multiple:true
        }

        section("To these temperatures") {
            input "heatingSetpoint", "decimal", title: "When Heating"
            input "coolingSetpoint", "decimal", title: "When Cooling"
        }

        section("Configuration") {
            input "dayOfWeek", "enum",
                title: "Which day of the week?",
                required: true,
                multiple: true,
                options: [
                    'All Week',
                    'Monday to Friday',
                    'Saturday & Sunday',
                    'Monday',
                    'Tuesday',
                    'Wednesday',
                    'Thursday',
                    'Friday',
                    'Saturday',
                    'Sunday'
                ],
                defaultValue: 'All Week'
            input "time", "time", title: "At this time", required: true
        }

        section("Operating Modes (optional)") {
            mode title: "Enable only when in this mode(s)", required: false, multiple: true // We need a mode since it can't be active for all modes
        }

        section("Notifications") {
            input("recipients", "contact", title: "Send notifications to (optional)", multiple: true, required: false) {
                paragraph "You can enter multiple phone numbers to send an SMS to by separating them with a '*'. E.g. 5551234567*4447654321"
                input "sms", "phone", title: "Send SMS to (phone number)", required: false
                input "push", "bool", title: "Send push notification", defaultValue: "true"
            }
        }
        
        section() {
            label title: "Assign a name for this SmartApp (optional)", required: false
            input name: "disableUpdateNotifications", title: "Don't check for new versions of the app", type: "bool", required: false
        }
    }
}

def installed() {
    // subscribe to these events
    log.debug "Installed called with $settings"
    initialize()
}

def updated() {
    // we have had an update
    // remove everything and reinstall
    log.debug "Updated called with $settings"
    initialize()
}

def initialize() {
    unschedule() // bug in ST platform, doesn't clear on running
    TimeZone timeZone = location.timeZone
    if (!timeZone) {
        timeZone = TimeZone.getDefault()
        log.error "Hub timeZone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
        sendPush "Hub timeZone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
    }

    // Check for new versions of the code
    def random = new Random()
    Integer randomHour = random.nextInt(18-10) + 10
    Integer randomDayOfWeek = random.nextInt(7-1) + 1 // 1 to 7
    schedule("0 0 " + randomHour + " ? * " + randomDayOfWeek, checkForCodeUpdate) // Check for code updates once a week at a random day and time between 10am and 6pm

    def scheduleTime = timeToday(time, timeZone)
    def timeNow = now() + (2*1000) // ST platform has resolution of 1 minutes, so be safe and check for 2 minutes) 
    log.debug "Current time is ${(new Date(timeNow)).format("EEE MMM dd yyyy HH:mm z", timeZone)}, scheduled time is ${scheduleTime.format("EEE MMM dd yyyy HH:mm z", timeZone)}"
    if (scheduleTime.time < timeNow) { // If we have passed current time we're scheduled for next day
        log.debug "Current scheduling check time $scheduleTime has passed, scheduling check for tomorrow"
        scheduleTime = scheduleTime + 1 // Next day schedule
    }
    log.debug "Scheduling Temp change for " + scheduleTime.format("EEE MMM dd yyyy HH:mm z", timeZone)
    schedule(scheduleTime, setTheTemp)
}

def setTheTemp() {
    def doChange = false
    Calendar localCalendar = Calendar.getInstance()
    localCalendar.setTimeZone(location.timeZone)
    int currentDayOfWeek = localCalendar.get(Calendar.DAY_OF_WEEK)

    // Check the condition under which we want this to run now
    // This set allows the most flexibility.
    if(dayOfWeek.contains('All Week')) {
        doChange = true
    }
    else if((dayOfWeek.contains('Monday') || dayOfWeek.contains('Monday to Friday')) && currentDayOfWeek == Calendar.instance.MONDAY) {
        doChange = true
    }

    else if((dayOfWeek.contains('Tuesday') || dayOfWeek.contains('Monday to Friday')) && currentDayOfWeek == Calendar.instance.TUESDAY) {
        doChange = true
    }

    else if((dayOfWeek.contains('Wednesday') || dayOfWeek.contains('Monday to Friday')) && currentDayOfWeek == Calendar.instance.WEDNESDAY) {
        doChange = true
    }

    else if((dayOfWeek.contains('Thursday') || dayOfWeek.contains('Monday to Friday')) && currentDayOfWeek == Calendar.instance.THURSDAY) {
        doChange = true
    }

    else if((dayOfWeek.contains('Friday') || dayOfWeek.contains('Monday to Friday')) && currentDayOfWeek == Calendar.instance.FRIDAY) {
        doChange = true
    }

    else if((dayOfWeek.contains('Saturday') || dayOfWeek.contains('Saturday & Sunday')) && currentDayOfWeek == Calendar.instance.SATURDAY) {
        doChange = true
    }

    else if((dayOfWeek.contains('Sunday') || dayOfWeek.contains('Saturday & Sunday')) && currentDayOfWeek == Calendar.instance.SUNDAY) {
        doChange = true
    }

    // some debugging in order to make sure things are working correclty
    log.debug "Calendar DOW: " + currentDayOfWeek
    log.debug "Configured DOW(s): " + dayOfWeek

    // If we have hit the condition to schedule this then lets do it
    if(doChange == true){
        log.debug "setTheTemp, location.mode = $location.mode, newMode = $newMode, location.modes = $location.modes"
        thermostat.setHeatingSetpoint(heatingSetpoint)
        thermostat.setCoolingSetpoint(coolingSetpoint)
        sendNotifications("$thermostat heat set to '${heatingSetpoint}' and cool to '${coolingSetpoint}'")
    }
    else {
        log.debug "Temp change not scheduled for today."
    }

    log.debug "Scheduling next check"

    initialize() // Setup the next check schedule
}

private void sendText(number, message) {
    if (number) {
        def phones = number.split("\\*")
        for (phone in phones) {
            sendSms(phone, message)
        }
    }
}

private void sendNotifications(message) {
    if (location.contactBookEnabled) {
        sendNotificationToContacts(message, recipients)
    } else {
        if (push) {
            sendPush message
        } else {
            sendNotificationEvent(message)
        }
        if (sms) {
            sendText(sms, message)
        }
    }
}

def checkForCodeUpdate(evt) {
    log.trace "Getting latest version data from the RBoy Apps server"
    
    def appName = "Individual Change Thermostat"
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
                    if (!disableUpdateNotifications) {
                        sendPush(msg)
                    }
                } else {
                    log.trace "No new app version found, latest version: $appVersion"
                }
                
                // Check device handler version updates
                def caps = [ thermostat ]
                caps?.each {
                    def devices = it?.findAll { it.hasAttribute("codeVersion") }
                    for (device in devices) {
                        if (device) {
                            def deviceName = device?.currentValue("dhName")
                            def deviceVersion = ret.data?."$deviceName"
                            if (deviceVersion && (deviceVersion > device?.currentValue("codeVersion"))) {
                                def msg = "New version of device ${device?.displayName} available: $deviceVersion, current version: ${device?.currentValue("codeVersion")}.\nPlease visit $serverUrl to get the latest version."
                                log.info msg
                                if (!disableUpdateNotifications) {
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