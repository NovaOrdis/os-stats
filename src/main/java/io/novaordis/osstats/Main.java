/*
 * Copyright (c) 2016 Nova Ordis LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.novaordis.osstats;

import io.novaordis.utilities.UserErrorException;

/**
 * @author Ovidiu Feodorov <ovidiu@novaordis.com>
 * @since 7/27/16
 */
public class Main {

    // Constants -------------------------------------------------------------------------------------------------------

    // Static ----------------------------------------------------------------------------------------------------------

    public static void main(String[] args) {

        try {

            Configuration conf = ConfigurationFactory.buildInstance(args);
            MainLoop mainLoop = new MainLoop(conf);
            mainLoop.run();
        }
        catch(UserErrorException e) {

            //
            // we know about this failure, it is supposed to go to stderr
            //

            Console.error(e.getMessage());
        }
        catch(Throwable t) {

            //
            // we don't expect that, provide more information
            //

            String msg = "internal failure: " + t.getClass().getSimpleName();
            if (t.getMessage() != null) {
                msg += ": " + t.getMessage();
            }
            msg += " (consult logs for more details)";
            Console.error(msg);
        }
    }

    // Attributes ------------------------------------------------------------------------------------------------------

    // Constructors ----------------------------------------------------------------------------------------------------

    // Public ----------------------------------------------------------------------------------------------------------

    // Package protected -----------------------------------------------------------------------------------------------

    // Protected -------------------------------------------------------------------------------------------------------

    // Private ---------------------------------------------------------------------------------------------------------

    // Inner classes ---------------------------------------------------------------------------------------------------

}
