;; jindent.el, used by $PTII/util/testsuite/jindent to
;; indent Java files to the Ptolemy II standard
;; Version: $Id$

(load (expand-file-name
       (concat (getenv "PTII") "/util/testsuite/ptjavastyle.el")))

(defun jindent ()
	(indent-region (point-min) (point-max) 'nil)
	(save-buffer)
)
