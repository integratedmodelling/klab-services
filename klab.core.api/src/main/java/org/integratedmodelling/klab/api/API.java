package org.integratedmodelling.klab.api;

/**
 * Stubs for "final" API description implementation
 */
public enum API implements Endpoint {

    CAPABILITIES() {
        @Override
        public String endpoint() {
            return "/capabilities";
        }

        @Override
        public Method[] methods() {
            return new Method[] {Method.GET};
        }
    },
    STATUS() {
        @Override
        public String endpoint() {
            return "/status";
        }

        @Override
        public Method[] methods() {
            return new Method[] {Method.GET};
        }

    };

    enum Method {
        GET, POST, PUT, DELETE;
    }

    enum ADMIN implements Endpoint {

        SHUTDOWN() {
            @Override
            public String endpoint() {
                return "/shutdown";
            }

            @Override
            public Method[] methods() {
                return new Method[0];
            }
        },
        CHECK_CREDENTIALS() {
            @Override
            public String endpoint() {
                return "/checkCredentials";
            }

            @Override
            public Method[] methods() {
                return new Method[0];
            }
        },
        SET_CREDENTIALS() {
            @Override
            public String endpoint() {
                return "/setCredentials";
            }

            @Override
            public Method[] methods() {
                return new Method[0];
            }
        },
        REMOVE_CREDENTIALS() {
            @Override
            public String endpoint() {
                return "/removeCredentials";
            }

            @Override
            public Method[] methods() {
                return new Method[0];
            }
        },
        LIST_CREDENTIALS() {
            @Override
            public String endpoint() {
                return "/listCredentials";
            }

            @Override
            public Method[] methods() {
                return new Method[0];
            }
        };

    }

    enum RESOURCES {

    }


}

interface Endpoint {
    String endpoint();
    API.Method[] methods();
}
