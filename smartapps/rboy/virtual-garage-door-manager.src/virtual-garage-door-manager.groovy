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
    return "01.04.01"
}

/**
 * Virtual Garage Door Manager
 *
 * Copyright RBoy Apps, redistribution or reuse any code is not allowed without permission
 * 2018-10-16 - (v01.04.01) Support for new ST app
 * 2018-04-12 - (v01.04.00) Added momemtary switch as an alternative to a relay
 * 2018-04-12 - (v01.03.00) Workaround to reduce logs in recent activity
 * 2018-03-02 - (v01.02.00) Increase relay contact to 2 seconds, 5 beeps with a 1 second delay before closing garage for better safety
 * 2018-02-24 - (v01.01.00) Optional acceleration sensor to detect opening/closing of garage door
 * 2018-02-23 - (v01.00.02) Refresh garage door states every 5 minutes
 * 2018-02-22 - (v01.00.01) Use capability switch for relay switches, allows more flexibility
 * 2018-02-20 - (v01.00.00) Initial version
 */

definition(
    name: "Virtual Garage Door Manager",
    namespace: "rboy",
    author: "RBoy Apps",
    description: "Build a custom garage door opener with relays, sensors and switches",
    category: "My Apps",
    iconUrl: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn@2x.png",
    singleInstance: true)

preferences {
	page(name: "setupDevices")
    page(name: "deviceSettings")
}

private getMaxDevices() { 5 } // 5 Virtual devices
private getAlertCount() { 5 } // 5 beeps per alert event

def setupDevices() {
    dynamicPage(name: "setupDevices", title: "Virtual Garage Door Manager v${clientVersion()}", install: true, uninstall: true) {
        section() {
            for (def i=1; i<=maxDevices; i++) {
                def dev = devices?.find { it.currentValue("slot") == i } // Show each installed device
                if (dev) {
                    def hrefParams = [num: i as String, dni: dev.deviceNetworkId as String, passed: true] // use as String otherwise it won't work on Android
                    href(name: "device${i}", params: hrefParams, title: dev.displayName, page: "deviceSettings", description: settings."delete${i}" ? "MARKED FOR DELETION" : "", required: false)
                } else {
                    def hrefParams = [num: i as String, passed: true] // use as String otherwise it won't work on Android
                    href(name: "device${i}", params: hrefParams, title: settings."name${i}" ?: "+ Add controller", page: "deviceSettings", description: settings."name${i}" ? (settingsComplete(i) ? "Ready to install" : "INCOMPLETE CONFIGURATION") : "", required: false)
                }
            }
        }
        
        section() {
            label title: "Assign a name for this SmartApp (optional)", required: false
            input name: "updateNotifications", title: "Check for new versions of the app", type: "bool", defaultValue: true, required: false
        }
    }
}

def deviceSettings(params) {
    //  params is broken, after doing a submitOnChange on this page, params is lost. So as a work around when this page is called with params save it to state and if the page is called with no params we know it's the bug and use the last state instead
    if (params.passed) {
        atomicState.params = params // We got something, so save it otherwise it's a page refresh for submitOnChange
    }

    def num
    def dni
    // Get user from the passed in params when the page is loading, else get from the last saved to work around not having params on pages
    if (params.num) {
        num = params.num
        dni = params.dni
        log.trace "Passed from main page, using params lookup for num $num, dni $dni"
    } else if (atomicState.params) {
        num = atomicState.params.num ?: 0
        dni = atomicState.params.dni ?: ""
        log.trace "Passed from submitOnChange, atomicState lookup for num $num, dni $dni"
    } else {
        log.error "Invalid params, no params found. Params: $params, saved params: $atomicState.params"
    }
    
    log.trace "Device Settings Page, passed params: $params, saved params:$atomicState.params"

    def dev
    if (dni) {
        dev = getChildDevice(dni)
    }
    
    dynamicPage(name:"deviceSettings", title: "${dev ? dev.displayName : "Controller"} Settings", uninstall: false, install: false) {
        section() {
            if (!dni) {
                input "name${num}", "text", title:"Name", description: "Controller name", required: true
            }
            
            if (!settings."relaym${num}") {
                input "relay${num}", "capability.switch", title:"Relay switch", description: "Relay that controls your garage door", required: true, submitOnChange: true
            }
            if (!settings."relay${num}" && !settings."relaym${num}") {
                paragraph "...OR..."
            }
            if (!settings."relay${num}") {
                input "relaym${num}", "capability.momentary", title:"Momentary switch", description: "Auto turn off relay that controls your garage door", required: true, submitOnChange: true
            }
            input "tilt${num}", "capability.contactSensor", title:"Open/Close sensor", description: "Sensor that monitors your garage door", required: true
            input "switch${num}", "capability.switch", title:"Toggle switch (optional)", description: "Switch(s) to open/close your garage door", multiple: true, required: false
            input "button${num}", "capability.button", title:"Toggle button (optional)", description: "Button(s) to open/close your garage door", multiple: true, required: false
            input "ring${num}", "capability.tone", title: "Alert sound (optional)", description: "Ring these device(s) when opening/closing the garage door", multiple: true, required: false
            input "movement${num}", "capability.accelerationSensor", title: "Movement sensor (optional)", description: "Detect if garage door was operated physically", multiple: true, required: false
            
            if (dni) {
                paragraph "", required: true
                input "delete${num}", "bool", title: "DELETE Controller", description: settings."delete${num}" ? "Marked for deletion" : "Tap button to delete", submitOnChange: true, required: false
            }
        }
    }
}

// Check if all device settings are entered
private settingsComplete(num) {
    return settings."name${num}"?.trim() && (settings."relay${num}" || settings."relaym${num}") && settings."tilt${num}"
}

// Delete all device settings for slot
private void settingsDelete(num) {
    deleteSetting("name${num}")
    deleteSetting("relay${num}")
    deleteSetting("relaym${num}")
    deleteSetting("tilt${num}")
    deleteSetting("switch${num}")
    deleteSetting("button${num}")
    deleteSetting("delete${num}")
}

// Get all installed devices
private getDevices() {
    def devs = getChildDevices()
    //log.trace "Installed devices found: $devs"
    return devs
}

private installAndCleanUpDevices() {
    log.trace "Setting up devices"
    
    // Use first hub
    def physicalHubs = location.hubs.findAll { it.type == physicalgraph.device.HubType.PHYSICAL } // Ignore Virtual hubs
    def hub = physicalHubs[0]
    
    // Get devices with complete configurations
    def installedDevs = devices
    for (def i=1; i<=maxDevices; i++) {
        def iDev = devices?.find { it.currentValue("slot") == i } // Check if we have an installed device on this slot
        if (iDev && settings."delete${i}") { // Check if the device is marked for deletion
            log.info "Device ${iDev.displayName} with device id ${iDev.deviceNetworkId} marked for deletion, deleting"
            try {
                deleteChildDevice(iDev.deviceNetworkId)
                settingsDelete(i as String) // clear the settings so it won't delete it again if reinstalled with same slot
            } catch (Exception e) {
                sendPush "Deleting device ${iDev.displayName} failed. Please check if device is in use by SmartApps"
                log.error "Deleting device ${iDev.displayName} failed. Error $e"
            }
        } else if (settingsComplete(i) && !iDev) { // Look for new devices which are ready to install
            try { // This may not succeed
                log.info "Installing device ${settings."name${i}"} installed with device id ${"VIRTUALGARAGE" + (i as String)}"
                def dev = addChildDevice("rboy", "Virtual Garage Door Controller Device Handler", "VIRTUALGARAGE" + (i as String), hub.id, ["label": settings."name${i}"])
                dev.setSlot(i as Integer) // Save the slot id for the device
            } catch (Exception e) {
                sendPush "Installing device ${settings."name${i}"} failed."
                log.error "Installing device ${settings."name${i}"} failed. Error $e"
            }
        }
    }
}

// APP STUFF
def installed() {
    log.trace "Installed called"
    initialize()
}

def updated() {
    log.trace "Updated called"
    initialize()
}

private def initialize() {
    log.debug "Initialize with settings: ${settings}"

	// Clear pending stuff
    unsubscribe()
    unschedule()

    // NOTE: RANDOM BUG in platform, we can't use subscribe after clean up because it seems to caching it and causing an exception, so clean up after subcribing if it's creating an issue
    // Device Setup
    installAndCleanUpDevices()
    
    state.alertCounter = [:]

    // Setup the subscriptions to events
    devices?.each { dev ->
        def slot = dev.currentValue("slot")
        subscribe(settings."tilt${slot}", "contact", tiltHandler)
        subscribe(settings."button${slot}", "button", buttonHandler)
        subscribe(settings."switch${slot}", "switch", switchHandler)
        subscribe(settings."movement${slot}", "acceleration", movementHandler)
    }
        
    // Initialize when we are going to check for code version updates
    TimeZone timeZone = location.timeZone
    if (!timeZone) {
        timeZone = TimeZone.getDefault()
        log.error "Hub location/timezone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
        sendPush "Hub location/timezone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
    }
    def random = new Random()
    Integer randomHour = random.nextInt(18-10) + 10
    Integer randomDayOfWeek = random.nextInt(7-1) + 1 // 1 to 7
    Calendar localCalendar = Calendar.getInstance(timeZone)
    localCalendar.set(Calendar.DAY_OF_WEEK, randomDayOfWeek)
    localCalendar.set(Calendar.HOUR_OF_DAY, randomHour) // Check for code updates once a week at a random day and time between 10am and 6pm
    localCalendar.set(Calendar.MINUTE, 0)
    localCalendar.set(Calendar.SECOND, 0)
    localCalendar.set(Calendar.MILLISECOND, 0)
    if (localCalendar.getTimeInMillis() < now()) { // If it's in the past add one week to it
        localCalendar.add(Calendar.DAY_OF_YEAR, 7)
    }
    state.nextCodeUpdateCheck = localCalendar.getTimeInMillis()
    log.debug "Checking for next app update after ${(new Date(state.nextCodeUpdateCheck)).format("EEE MMM dd yyyy HH:mm z", timeZone)}"
    
    // Housekeeping stuff
    runEvery1Minute(houseKeeping)
    
    // Refresh all devices
    refreshAllDevices()
}


// SUBSCRIPTION HANDLERS
// Called when garage door tilt sensor detects a state change
def tiltHandler(evt) {
    log.debug "Tilt: name $evt.name, value $evt.value, device $evt.device"
    
    def dev = devices.find { settings."tilt${it.currentValue("slot")}"?.id == evt.device.id } // find the corresponding device for this tilt sensor
    log.info "$dev.displayName is ${evt.value == "open" ? "Open" : "Closed"}"
    
    if (evt.value == "open") { // Set the garage door state
        dev.reportEvent([name: "door", value: "open"])
        dev.reportEvent([name: "contact", value: "open"])
        dev.reportEvent([name: "switch", value: "on"])
    } else {
        dev.reportEvent([name: "door", value: "closed"])
        dev.reportEvent([name: "contact", value: "closed"])
        dev.reportEvent([name: "switch", value: "off"])
    }
}

// Called when button activity is detected
def buttonHandler(evt) {
    log.debug "Button: name $evt.name, value $evt.value, device $evt.device"
    
    def dev = devices.find { dev -> settings."button${dev.currentValue("slot")}".any { it?.id == evt.device.id } } // find the corresponding device for this button
    log.debug "Toggling $dev.displayName"
    
    dev?.push()
}

// Called when switch activity is detected
def switchHandler(evt) {
    log.debug "Switch: name $evt.name, value $evt.value, device $evt.device"
    
    def dev = devices.find { dev -> settings."switch${dev.currentValue("slot")}".any { it?.id == evt.device.id } } // find the corresponding device for this switch
    log.debug "Toggling $dev.displayName"
    
    dev?.push() // A switch acts like a button, just push it
}

// Called when the acceleration sensor detects movement
def movementHandler(evt) {
    log.debug "Movement: name $evt.name, value $evt.value, device $evt.device"

    def dev = devices.find { settings."movement${it.currentValue("slot")}"?.id == evt.device.id } // find the corresponding device for this movement sensor
    def tilt = settings."tilt${it.currentValue("slot")}" // find the corresponding tilt sensor for this device
    
    if (evt.value == "active") { // Movement started
        def doorState
        switch (tilt.currentValue("door")) { // If the door has been operated from a physical button
            case "open":
            	doorState = "closing"
                break
                
            case "closed":
            	doorState = "opening"
                break
                
            default: // Other states are to be ignore as they would have started operation remotely
            	break
        }
        
        if (doorState) {
            log.info "$dev.displayName is ${doorState}"
            dev.reportEvent([name: "door", value: doorState])
        }
    }
}


// CALLBACKS FROM DEVICE HANDLER
// Open garage door
def open(child) {
    def dev = devices.find { it.id == child.device.id }
    if ((dev.currentValue("door") == "open") || (dev.currentValue("door") == "opening")) {
        log.trace "Door already open or opening, ignoring command"
        return
    }
    def slot = dev.currentValue("slot")
    def relay = settings."relay${slot}" ?: settings."relaym${slot}"
    def alert = settings."ring${slot}"
    log.info "Open called for controller $dev from slot $slot, turning on $relay, ringing $alert"
    
    dev.reportEvent([name: "door", value: "opening"]) // Set state
    alert*.beep() // We don't need delayed alerts for opening, just one will suffice
    if (settings."relay${slot}") {
        relay?.on() // Turn the relay on
        runIn(2, off, [data: [id: dev.id, slot: slot]]) // Turn it off after 2 seconds
    } else {
        relay?.push()
    }
}

// Close Garage Door
def close(child) {
    def dev = devices.find { it.id == child.device.id }
    if ((dev.currentValue("door") == "closed") || (dev.currentValue("door") == "closing")) {
        log.trace "Door already open or opening, ignoring command"
        return
    }
    def slot = dev.currentValue("slot")
    def relay = settings."relay${slot}" ?: settings."relaym${slot}"
    def alert = settings."ring${slot}"
    log.info "Close called for controller $dev from slot $slot, turning on $relay, ringing $alert"
    
    dev.reportEvent([name: "door", value: "closing"]) // Set state
    if (alert) { // If we have an alerting device then execute that for 5 seconds and then open
        runAlertSequence([id: dev.id, slot: slot])
    } else {
        if (settings."relay${slot}") {
            relay?.on() // Turn the relay on
            runIn(2, off, [data: [id: dev.id, slot: slot]]) // Turn it off after 2 seconds
        } else {
            relay?.push()
        }
    }
}

// Refresh state of garage door
def refresh(child) {
    def dev = devices.find { it.id == child.device.id }
    def slot = dev.currentValue("slot")
    log.trace "Refresh called for $dev from slot $slot"
    
    def tilt = settings."tilt${slot}"
    def tiltVal = tilt?.currentValue("contact") // At initialization we may not have a tilt sensor assigned yet
    log.info "Current state of $tilt is ${tiltVal}"
    if (tiltVal == "open") { // Set the garage door state
        dev.reportEvent([name: "door", value: "open"])
        dev.reportEvent([name: "contact", value: "open"])
        dev.reportEvent([name: "switch", value: "on"])
    } else if (tiltVal == "closed") {
        dev.reportEvent([name: "door", value: "closed"])
        dev.reportEvent([name: "contact", value: "closed"])
        dev.reportEvent([name: "switch", value: "off"])
    } else {
        dev.reportEvent([name: "door", value: "unknown"])
    }
}


// LOCAL CALLBACKS
// Run the alert sequence to beep the device before activating relay
def runAlertSequence(data) {
    def slot = data?.slot
    def relay = settings."relay${slot}" ?: settings."relaym${slot}"
    def alert = settings."ring${slot}"
    log.trace "Run alert sequence called with data $data from slot $slot, turning on $relay, ringing $alert, action $action, counter ${state.alertCounter?.slot}"

    if (!state.alertCounter) { // Initialize it if not there after an upgrade
        state.alertCounter = [:]
    }
    
    if ((state.alertCounter.slot = (state.alertCounter.slot ?: 0) + 1) < alertCount) {
        alert*.beep() // Ring the alert devices
        runIn(1, runAlertSequence, [data: data, overwrite: false]) // Beep every 1 second, each garage can have it's own action timer
    } else {
        state.alertCounter.slot = 0 // Reset it
        if (settings."relay${slot}") {
            relay?.on() // Turn the relay on
            runIn(2, off, [data: data]) // Turn it off after 2 seconds
        } else {
            relay?.push()
        }
    }
}

// Delayed call back handlers for relays
def on(data) {
    log.trace "Delayed On called for ${data}"
    
    def dev = devices.find { it.id == data.id } // Find out device
    def relay = settings."relay${data.slot}"
    log.info "Turning on $relay for controller $dev"
    relay.on()
}

def off(data) {
    log.trace "Delayed Off called for ${data}"
    
    def dev = devices.find { it.id == data.id } // Find out device
    def relay = settings."relay${data.slot}"
    log.info "Turning off $relay for controller $dev"
    relay.off()
}



// Refresh the state of all devices
private refreshAllDevices() {
    // Refresh all devices
    log.trace "Refreshing all devices"
    devices.each { dev ->
        dev.refresh()
    }
}

// House keeping
def houseKeeping() {
    // We check for a code update once a week
    TimeZone timeZone = location.timeZone
    if (!timeZone) {
        timeZone = TimeZone.getDefault()
        log.error "Hub location/timezone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
        sendPush "Hub location/timezone not set, using ${timeZone.getDisplayName()} timezone. Please set Hub location and timezone for the codes to work accurately"
    }
    if (now() >= state.nextCodeUpdateCheck) {
        // Before checking for code update, calculate the next time we want to check
        state.nextCodeUpdateCheck = (state.nextCodeUpdateCheck ?: now()) + (7*24*60*60*1000) // 1 week from now
        log.info "Checking for next app update after ${(new Date(state.nextCodeUpdateCheck)).format("EEE MMM dd yyyy HH:mm z", timeZone)}"
        
        checkForCodeUpdate() // Check for code updates
    } else {
        log.trace "Checking for next app update after ${(new Date(state.nextCodeUpdateCheck)).format("EEE MMM dd yyyy HH:mm z", timeZone)}"
    }

    // Refresh all devices current state
    refreshAllDevices()
}

// Temporarily override the user settings
private updateSetting(name, value) {
    app.updateSetting(name, value) // For SmartApps and is retained across page refreshes
    //settings[name] = value // For Device Handlers and SmartApps
    //settings.name = value // For Device Handlers and SmartApps
}

private deleteSetting(name) {
    //app.deleteSetting(name) // For SmartApps delete it, TODO: Gives and error
    //settings.remove(name) // For Device Handlers
    clearSetting(name) // For SmartApps
}

private clearSetting(name) {
    app.updateSetting(name, '') // For SmartApps 
}

def checkForCodeUpdate(evt) {
    log.trace "Getting latest version data from the RBoy Apps server"
    
    def appName = "Virtual Garage Door Manager"
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
                def caps = [ devices ]
                caps?.each {
                    def devices = it?.findAll { it.hasAttribute("codeVersion") }
                    for (device in devices) {
                        if (device) {
                            def deviceName = device?.currentValue("dhName")
                            def deviceVersion = ret.data?."$deviceName"
                            if (deviceVersion && (deviceVersion > device?.currentValue("codeVersion"))) {
                                def msg = "New version of device handler for ${device?.displayName} available: $deviceVersion, current version: ${device?.currentValue("codeVersion")}.\nPlease visit $serverUrl to get the latest version."
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