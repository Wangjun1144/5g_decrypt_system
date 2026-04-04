#ifndef WS_CORE_GCRYPT_H
#define WS_CORE_GCRYPT_H

/*
 * Temporary preflight shim for libgcrypt headers.
 *
 * This is only used while compiler-driving the minimum Wireshark slice. The
 * real ws-core runtime should eventually point at actual libgcrypt headers and
 * libraries.
 */

#include <stdarg.h>

#define GCRYPT_VERSION "preflight-shim"
#define GCRYPT_VERSION_NUMBER 0x010a00

#define GCRY_LOG_CONT 0
#define GCRY_LOG_DEBUG 1
#define GCRY_LOG_INFO 2
#define GCRY_LOG_WARN 3
#define GCRY_LOG_BUG 4
#define GCRY_LOG_ERROR 5
#define GCRY_LOG_FATAL 6

#define GCRYCTL_NO_FIPS_MODE 1
#define GCRYCTL_DISABLE_SECMEM 2
#define GCRYCTL_INITIALIZATION_FINISHED 3

#define GCRY_CIPHER_AES128 101
#define GCRY_CIPHER_MODE_CTR 202

typedef void (*gcry_handler_no_mem_t)(void);
typedef void (*gcry_handler_error_t)(void);
typedef void (*gcry_handler_log_t)(void *opaque, int level, const char *format, va_list args);
typedef int gcry_error_t;
typedef struct gcry_sexp *gcry_sexp_t;
typedef struct gcry_cipher_handle *gcry_cipher_hd_t;

const char *gcry_check_version(const char *req_version);
void gcry_control(int cmd, ...);
void gcry_set_log_handler(gcry_handler_log_t func, void *opaque);
gcry_error_t gcry_err_code(gcry_error_t err);
gcry_error_t gcry_cipher_open(gcry_cipher_hd_t *handle, int algo, int mode, unsigned int flags);
gcry_error_t gcry_cipher_setkey(gcry_cipher_hd_t handle, const void *key, size_t keylen);
gcry_error_t gcry_cipher_setctr(gcry_cipher_hd_t handle, const void *ctr, size_t ctrlen);
gcry_error_t gcry_cipher_decrypt(gcry_cipher_hd_t handle, void *out, size_t outsize, const void *in, size_t inlen);
void gcry_cipher_close(gcry_cipher_hd_t handle);

#endif
